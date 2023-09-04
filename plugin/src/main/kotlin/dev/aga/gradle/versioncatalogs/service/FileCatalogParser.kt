package dev.aga.gradle.versioncatalogs.service

import dev.aga.gradle.versioncatalogs.exception.ConfigurationException
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
                ?: throw ConfigurationException(
                    "${libraryName} not found in catalog file ${file.absolutePath}",
                )

        val versions = toml["versions"]?.let { it as? TomlTable }

        return getGAV(libraryName, library, versions)
    }

    private fun getGAV(libraryName: String, library: TomlTable, versions: TomlTable?): Dependency {
        val (group, name) =
            if (library["module"] is String) {
                val split = (library["module"] as String).split(":")
                split[0] to split[1]
            } else {
                val group =
                    library["group"]?.let { it as? String }
                        ?: throw ConfigurationException(
                            "Group not found for library ${libraryName} in catalog file ${file.absolutePath}",
                        )
                val name =
                    library["name"]?.let { it as? String }
                        ?: throw ConfigurationException(
                            "Name not found for library ${libraryName} in catalog file ${file.absolutePath}",
                        )
                group to name
            }

        val version = getVersion(libraryName, library, versions)
        return Dependency().apply {
            groupId = group
            artifactId = name
            this.version = version
        }
    }

    private fun getVersion(libraryName: String, library: TomlTable, versions: TomlTable?): String {
        library.getString("version.ref")?.let {
            return versions?.getString(it)
                ?: throw ConfigurationException(
                    "Version ref '${it}' for library ${libraryName} not found in catalog file ${file.absolutePath}",
                )
        }

        return library.getString("version")
            ?: throw ConfigurationException(
                "Version not found for library ${libraryName} in catalog file ${file.absolutePath}",
            )
    }

    private fun parseCatalog(file: File) = Toml.parse(file.toPath())
}
