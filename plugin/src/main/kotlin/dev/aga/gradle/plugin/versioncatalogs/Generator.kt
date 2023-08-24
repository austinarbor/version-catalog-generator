package dev.aga.gradle.plugin.versioncatalogs

import dev.aga.gradle.plugin.versioncatalogs.service.POMFetcher
import dev.aga.gradle.plugin.versioncatalogs.toml.CatalogParser
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.slf4j.LoggerFactory

object Generator {

    private val logger = LoggerFactory.getLogger(Generator::class.java)
    private val jarFilter = { dep: Dependency -> null == dep.type || "jar" == dep.type }
    private val importFilter = { dep: Dependency -> "pom" == dep.type && "import" == dep.scope }

    /**
     * Generate a version catalog with the provided [name].
     *
     * @param name the name of the version catalog
     * @param conf the generator configuration options
     * @return the [VersionCatalogBuilder]
     */
    fun MutableVersionCatalogContainer.generate(
        name: String,
        conf: GeneratorConfig.() -> Unit,
    ): VersionCatalogBuilder {
        val config = GeneratorConfig().apply(conf)
        val bomDep =
            CatalogParser.findBom(config.sourceCatalogFile, config.sourceLibraryNameInCatalog)
        return create(name) {
            val props = mutableMapOf<String, String>()
            val seenModules = mutableSetOf<String>()
            val queue = ArrayDeque(listOf(bomDep))
            while (queue.isNotEmpty()) {
                val dep = queue.removeFirst()
                val pom = POMFetcher.fetchPOM(config.repoBaseUrl, dep)
                loadBom(pom, config, queue, props, seenModules)
            }
        }
    }

    /**
     * Traverse the BOM and create a version reference for each property value. If the BOM contains
     * properties with names we have previously seen, we will ignore that version and the
     * dependencies mapped to that version.
     *
     * @param model the BOM
     * @param config [GeneratorConfig]
     * @param queue the BFS queue to add more BOMs into
     * @param props the version properties
     * @param seenModules the set of modules we have already created libraries for
     */
    internal fun VersionCatalogBuilder.loadBom(
        model: Model,
        config: GeneratorConfig,
        queue: MutableList<Dependency>,
        props: MutableMap<String, String>,
        seenModules: MutableSet<String>,
    ) {
        val (newProps, dupes) = getProperties(model, config.versionNameGenerator, props.keys)
        if (dupes.isNotEmpty()) {
            logger.warn(
                "found {} duplicate version keys while loading bom {}:{}:{}",
                dupes.size,
                model.groupId,
                model.artifactId,
                model.version,
            )
        }
        newProps.forEach { (key, value) -> version(key, value) }
        props.putAll(newProps)
        loadDependencies(model, config, queue, props, dupes, seenModules)
    }

    /**
     * Traverse the BOM and create a library from each dependency. Any dependency that has a version
     * that exists as a key in [excludedProps] will be ignored. Any dependencies we encounter in the
     * BOM that have `type == "pom" && scope == "import"` will be added to the [queue].
     *
     * @param model the BOM
     * @param config [GeneratorConfig]
     * @param queue the BFS queue to add more BOMs into
     * @param props the version properties
     * @param excludedProps the set of version properties to ignore
     * @param seenModules the set of modules we have already created libraries for
     */
    internal fun VersionCatalogBuilder.loadDependencies(
        model: Model,
        config: GeneratorConfig,
        queue: MutableList<Dependency>,
        props: Map<String, String>,
        excludedProps: Set<String>,
        seenModules: MutableSet<String>,
    ) {
        getNewDependencies(model, config, seenModules, importFilter).forEach { (version, boms) ->
            boms.forEach { bom ->
                val (_, isRef) = createLibrary(bom, version, props, config)
                // if the version is a property, replace it with the
                // actual version value
                if (isRef) {
                    bom.version = props[version]
                }
                queue.add(bom)
            }
        }

        getNewDependencies(model, config, seenModules, jarFilter)
            .filter { skipAndLogExcluded(model, it, excludedProps) }
            .forEach { (version, deps) ->
                val aliases = mutableListOf<String>()
                deps.forEach { dep ->
                    val (alias, isRef) = createLibrary(dep, version, props, config)
                    if (isRef) {
                        aliases += alias
                    }
                }
                if (aliases.isNotEmpty()) {
                    bundle(version, aliases)
                }
            }
    }

    /**
     * Create a library from the given information. If the [version] exists as a key in properties
     * then the library will be created with a versionRef to it. Otherwise, the version will be set
     * directly on the library
     *
     * @param dep the dependency
     * @param version the version of the dependency, may be a property of actual version
     * @param props the version properties
     * @param config the [GeneratorConfig]
     * @return the library's alias and true if the version was a reference, or false if it was not
     */
    internal fun VersionCatalogBuilder.createLibrary(
        dep: Dependency,
        version: String,
        props: Map<String, String>,
        config: GeneratorConfig,
    ): Pair<String, Boolean> {
        val alias = config.libraryAliasGenerator(dep.groupId, dep.artifactId)
        val library = library(alias, dep.groupId, dep.artifactId)
        return if (props.containsKey(version)) {
            // aliases += alias
            library.versionRef(version)
            alias to true
        } else {
            library.version(version)
            alias to false
        }
    }

    fun getNewDependencies(
        model: Model,
        config: GeneratorConfig,
        seenModules: MutableSet<String> = mutableSetOf(),
        filter: (Dependency) -> Boolean,
    ): Map<String, List<Dependency>> {
        return model.dependencyManagement.dependencies
            .asSequence()
            .onEach { it.groupId = mapGroup(model, it.groupId) }
            .filter(filter)
            .filter { seenModules.add("${it.groupId}:${it.artifactId}") }
            .onEach { it.version = mapVersion(model, it.version, config.versionNameGenerator) }
            .groupBy { it.version }
    }

    fun getProperties(
        model: Model,
        versionMapper: (String) -> String,
        existingProperties: Set<String> = setOf(),
    ): Pair<Map<String, String>, Set<String>> {
        val props = model.properties
        val (newProps, dupes) =
            props
                .propertyNames()
                .asSequence()
                .mapNotNull { it as? String }
                .map { mapVersion(model, it, versionMapper) to props.getProperty(it) }
                .partition { !existingProperties.contains(it.first) }

        // turn the dupes into a set of strings
        val dupeVersions = dupes.asSequence().map { it.first }.toSet()

        return newProps.toMap() to dupeVersions
    }

    fun mapGroup(model: Model, group: String): String {
        if ("\${project.groupId}" == group) {
            return model.groupId
        }
        return group
    }

    fun mapVersion(model: Model, version: String, versionMapper: (String) -> String): String {
        if ("\${project.version}" == version) {
            return model.version
        }
        // remove leading ${ and ending } if they exist
        val v = version.removePrefix("\${").trimEnd('}')
        return versionMapper(v)
    }

    private fun skipAndLogExcluded(
        source: Model,
        e: Map.Entry<String, List<Dependency>>,
        excludedProps: Set<String>,
    ): Boolean {
        val (version, deps) = e
        if (excludedProps.contains(version)) {
            deps.forEach {
                logger.warn(
                    "excluding {}:{}:{} found in {}:{}:{}",
                    it.groupId,
                    it.artifactId,
                    it.version,
                    source.groupId,
                    source.artifactId,
                    source.version,
                )
            }
            return false
        } else {
            return true
        }
    }
}
