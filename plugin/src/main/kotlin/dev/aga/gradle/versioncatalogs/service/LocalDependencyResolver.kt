package dev.aga.gradle.versioncatalogs.service

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader

class LocalDependencyResolver(private val rootDir: Path) : DependencyResolver {
    constructor(rootDir: String) : this(Paths.get(rootDir))

    override fun resolve(notation: Any): Model {
        return when (notation) {
            is String -> resolve(notation)
            is File -> resolve(notation.toPath())
            is Path -> resolve(notation)
            is Dependency -> resolve(Paths.get("${notation.artifactId}-${notation.version}.pom"))
            else ->
                throw RuntimeException(
                    "LocalDependencyResolver can only resolve File, Path, or Dependency notations",
                )
        }
    }

    private fun resolve(notation: String): Model {
        val pieces = notation.split(":")
        val (artifact, version) =
            when (pieces.size) {
                3 -> 1 to 2
                2 -> 0 to 1
                else -> throw RuntimeException("Unable to parse notation '${notation}")
            }

        return resolve(Paths.get("${pieces[artifact]}-${pieces[version]}.pom"))
    }

    private fun resolve(file: Path): Model {
        val reader = MavenXpp3Reader()
        val filePath = rootDir.resolve(file)
        if (filePath.exists()) {
            return Files.newBufferedReader(filePath, StandardCharsets.UTF_8).use { reader.read(it) }
        }
        throw RuntimeException("Path ${filePath} does not exist")
    }
}
