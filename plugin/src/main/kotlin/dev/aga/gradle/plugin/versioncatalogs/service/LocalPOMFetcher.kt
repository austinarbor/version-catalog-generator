package dev.aga.gradle.plugin.versioncatalogs.service

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.exists
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader

class LocalPOMFetcher(private val rootDir: String) : POMFetcher {

    override fun fetch(dep: Dependency): Model {
        val reader = MavenXpp3Reader()
        val path = Paths.get(rootDir, "${dep.artifactId}.pom")
        if (path.exists()) {
            return Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader.read(it) }
        }
        throw RuntimeException("Path ${path} does not exist")
    }
}
