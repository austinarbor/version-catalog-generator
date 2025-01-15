package dev.aga.gradle.versioncatalogs

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.create

class VersionCatalogGeneratorPlugin : Plugin<Settings> {

  override fun apply(settings: Settings) {
    settings.extensions.create("generator", VersionCatalogGeneratorPluginExtension::class, settings)
  }
}
