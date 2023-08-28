package dev.aga.gradle.versioncatalogs.service

import java.io.File
import org.apache.maven.model.Dependency
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable

internal class FileCatalogParser(private val file: File) : CatalogParser {

    override fun findLibrary(name: String): Dependency {
        val parsed = parseCatalog(file)
        return findBom(parsed, name)
    }

    private fun findBom(toml: TomlParseResult, libraryName: String): Dependency {
        val library =
            toml["libraries"]
                ?.let { it as? TomlTable }
                ?.let { it[libraryName] }
                ?.let { it as? TomlTable }
                ?: throw RuntimeException("${libraryName} not found in catalog file")

        val versions = toml["versions"]?.let { it as? TomlTable }

        return getGAV(library, versions)
    }

    private fun getGAV(library: TomlTable, versions: TomlTable?): Dependency {
        val (group, name) =
            if (library["module"] is String) {
                val split = (library["module"] as String).split(":")
                split[0] to split[1]
            } else {
                val group =
                    library["group"]?.let { it as? String }
                        ?: throw RuntimeException("Group not found ")
                val name =
                    library["name"]?.let { it as? String }
                        ?: throw RuntimeException("Name not found")
                group to name
            }

        val version = getVersion(library, versions)
        return Dependency().apply {
            groupId = group
            artifactId = name
            this.version = version
        }
    }

    private fun getVersion(library: TomlTable, versions: TomlTable?): String {
        library.getString("version.ref")?.let {
            return versions?.getString(it)
                ?: throw RuntimeException("Version ref '${it}' not found")
        }

        return library.getString("version") ?: throw RuntimeException("Version not found")
    }

    private fun parseCatalog(file: File) = Toml.parse(file.toPath())
}
