package dev.aga.gradle.versioncatalogs.service

import org.apache.maven.model.Dependency
import org.apache.maven.model.Model

interface DependencyResolver {
    fun resolve(notation: Any): Model

    fun resolve(dep: Dependency): Model {
        val resolved = resolve("${dep.groupId}:${dep.artifactId}:${dep.version}")
        // fill in any details that are missing in the artifact itself from what
        // we know
        return resolved.apply {
            groupId = groupId ?: dep.groupId
            artifactId = artifactId ?: dep.artifactId
            version = version ?: dep.version
        }
    }

    fun resolve(groupId: String, artifactId: String, version: String): Model {
        val dep =
            Dependency().apply {
                this.groupId = groupId
                this.artifactId = artifactId
                this.version = version
            }
        return resolve(dep)
    }
}
