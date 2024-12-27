package dev.aga.gradle.versioncatalogs.model

import dev.aga.gradle.versioncatalogs.service.CatalogParser

class TomlVersionRef(
    private val versionRef: String,
    private val catalogParserSupplier: () -> CatalogParser,
) : PropertyOverride {
    constructor(
        versionRef: String,
        catalogParser: CatalogParser,
    ) : this(versionRef, { catalogParser })

    override fun getValue(): String? {
        return catalogParserSupplier().findVersion(versionRef)
    }
}
