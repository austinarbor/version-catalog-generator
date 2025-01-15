package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.model.TomlVersionRef
import dev.aga.gradle.versioncatalogs.service.FileCatalogParser
import java.io.File

fun File.versionRef(alias: String): TomlVersionRef {
  return TomlVersionRef(alias, FileCatalogParser(this))
}
