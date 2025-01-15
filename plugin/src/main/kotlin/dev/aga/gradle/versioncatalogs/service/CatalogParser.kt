package dev.aga.gradle.versioncatalogs.service

import org.apache.maven.model.Dependency

interface CatalogParser {
  fun findLibrary(name: String): Dependency

  fun findVersion(alias: String): String?
}
