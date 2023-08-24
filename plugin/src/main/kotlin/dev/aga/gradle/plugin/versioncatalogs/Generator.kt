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
     * Generate a version catalog with the provided name
     *
     * @param name the name of the version catalog
     * @param conf the generator configuration options
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
                // if the version is a property, replace it with the
                // actual version value
                if (props.containsKey(version)) {
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
                    val alias = config.libraryAliasGenerator(dep.groupId, dep.artifactId)
                    val library = library(alias, dep.groupId, dep.artifactId)
                    if (props.containsKey(version)) {
                        aliases += alias
                        library.versionRef(version)
                    } else {
                        library.version(version)
                    }
                }
                if (aliases.isNotEmpty()) {
                    bundle(version, aliases)
                }
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
            .filter { seenModules.add("${it.groupId}:${it.artifactId}") }
            .filter(filter)
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
