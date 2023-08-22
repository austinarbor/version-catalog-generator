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
        val dependencies = getNewDependencies(model, config, seenModules)

        dependencies.forEach { (version, deps) ->
            val (imports, rest) = deps.partition { "pom" == it.type && "import" == it.scope }
            imports.forEach {
                if (props.containsKey(it.version)) {
                    it.version = props[it.version]
                }
                queue.add(it)
            }
            val aliases = mutableListOf<String>()
            rest
                .filter { null == it.type || "jar" == it.type }
                .filter {
                    if (excludedProps.contains(version)) {
                        logger.warn(
                            "excluding {}:{}:{} found in {}:{}:{}",
                            it.groupId,
                            it.artifactId,
                            it.version,
                            model.groupId,
                            model.artifactId,
                            model.version,
                        )
                        false
                    } else {
                        true
                    }
                }
                .forEach { dep ->
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
    ): Map<String, List<Dependency>> {
        return model.dependencyManagement.dependencies
            .asSequence()
            .map { it.apply { groupId = mapGroup(model, it.groupId) } }
            .filter { seenModules.add("${it.groupId}:${it.artifactId}") }
            .map {
                it.apply { version = mapVersion(model, it.version, config.versionNameGenerator) }
            }
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
}
