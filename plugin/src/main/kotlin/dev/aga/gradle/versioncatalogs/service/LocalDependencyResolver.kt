package dev.aga.gradle.versioncatalogs.service

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader

class LocalDependencyResolver(private val path: Path) {
  fun resolve(): Model {
    require(path.exists()) { "Path ${path} does not exist" }

    val reader = MavenXpp3Reader()
    return Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader.read(it) }
  }
}
