package dev.aga.gradle.plugin.versioncatalogs

import dev.aga.gradle.plugin.versioncatalogs.service.POMFetcher
import dev.aga.gradle.plugin.versioncatalogs.toml.CatalogParser
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer

object Generator {

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
        val pom = POMFetcher.fetchPOM(config.repoBaseUrl, bomDep)
        val props = getProperties(pom, config.versionNameGenerator)
        return create(name) {
            props.forEach { (key, value) ->
                if (!Character.isDigit(key[0])) {
                    version(key, value)
                }
            }
            val dependencies = getDependencies(pom, config.versionNameGenerator)
            dependencies.asSequence().forEach { (version, deps) ->
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
    }

    fun getDependencies(
        model: Model,
        versionMapper: (String) -> String,
    ): Map<String, List<Dependency>> {
        val props = getProperties(model, versionMapper)
        val seen = mutableSetOf<String>()
        val grouped =
            model.dependencyManagement.dependencies
                .asSequence()
                .filter { null == it.type || "jar" == it.type }
                .filter { seen.add("${it.groupId}:${it.artifactId}") }
                .map {
                    it.version = mapVersion(it.version, versionMapper)
                    it
                }
                .groupBy { it.version }
        return grouped
    }

    fun getProperties(model: Model, versionMapper: (String) -> String): Map<String, String> {
        val props = model.properties
        return props
            .propertyNames()
            .asSequence()
            .mapNotNull { it as? String }
            .map { mapVersion(it, versionMapper) to props.getProperty(it) }
            .toMap()
    }

    fun mapVersion(version: String, versionMapper: (String) -> String): String {
        // remove leading ${ and ending } if they exist
        val v = version.removePrefix("\${").trimEnd('}')
        return versionMapper(v)
    }
}
