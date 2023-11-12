package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.Generator.generate
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.model.ObjectFactory

open class VersionCatalogGeneratorPluginExtension
@Inject
constructor(
    private val settings: Settings,
    val objects: ObjectFactory,
) {

    lateinit var config: GeneratorConfig

    /**
     * Generate a version catalog with the provided [name]. This is mostly used as an entry point
     * for the Gradle dsl
     *
     * @param name the name to use for the generated catalog
     * @param conf the [VersionCatalogGeneratorPluginExtension] configuration
     * @return the [VersionCatalogBuilder]
     */
    fun generate(
        name: String,
        conf: Action<GeneratorConfig>,
    ): VersionCatalogBuilder {
        return settings.generate(name, conf)
    }
}
