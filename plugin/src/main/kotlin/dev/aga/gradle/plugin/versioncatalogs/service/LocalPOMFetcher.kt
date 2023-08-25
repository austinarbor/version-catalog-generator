package dev.aga.gradle.plugin.versioncatalogs.service

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader

class LocalPOMFetcher(private val rootDir: String) : POMFetcher {

    override fun fetch(dep: Dependency): Model {
        val reader = MavenXpp3Reader()
        val path = Paths.get(rootDir, "${dep.artifactId}.xml")
        return Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader.read(it) }
    }
}
