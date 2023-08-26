package dev.aga.gradle.plugin.versioncatalogs.service

import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.gradle.api.artifacts.Dependency as GradleDependency

interface POMFetcher {
    fun fetch(dep: Dependency): Model

    fun fetch(groupId: String, artifactId: String, version: String): Model {
        val dep =
            Dependency().apply {
                this.groupId = groupId
                this.artifactId = artifactId
                this.version = version
            }
        return fetch(dep)
    }

    fun fetch(dep: GradleDependency): Model {
        return fetch(dep.group!!, dep.name, dep.version!!)
    }
}
