package dev.aga.gradle.versioncatalogs

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

class VersionCatalogGeneratorPlugin : Plugin<Settings> {
    // nothing to do in apply, all our logic
    // is in the extension function
    override fun apply(settings: Settings) {}
}
