package dev.aga.gradle.plugin.versioncatalogs.service

import java.net.URL
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader

internal class RemotePOMFetcher(private val baseUrl: String) : POMFetcher {

    override fun fetch(dep: Dependency): Model {
        val groupIdPath = splitGroupId(dep.groupId)
        val urlString =
            "${baseUrl}/${groupIdPath}/${dep.artifactId}/${dep.version}/${dep.artifactId}-${dep.version}.pom"
        val reader = MavenXpp3Reader()
        return URL(urlString)
            .openStream()
            .use { reader.read(it) }
            .takeIf { it.packaging == "pom" }
            ?.also {
                if (it.version == null) {
                    it.version = dep.version
                }
            }
            ?: throw RuntimeException("Invalid pom file")
    }

    private fun splitGroupId(groupId: String): String {
        return groupId.split(".").joinToString("/")
    }
}
