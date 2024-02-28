package dev.aga.gradle.versioncatalogs.model

import dev.aga.gradle.versioncatalogs.service.CatalogParser

class TomlVersionRef(private val catalogParser: CatalogParser, private val versionRef: String) :
    PropertyOverride {
    override fun getValue(): String? {
        return catalogParser.findVersion(versionRef)
    }
}
