package dev.aga.gradle.plugin.versioncatalogs.service

import org.apache.maven.model.Dependency

interface CatalogParser {
    fun findLibrary(name: String): Dependency
}
