package dev.aga.gradle.versioncatalogs.service

import org.apache.maven.model.Dependency
import org.apache.maven.model.Model

interface DependencyResolver {

    fun resolve(source: Dependency): Pair<Model, Model?>

    fun resolve(groupId: String, artifactId: String, version: String): Pair<Model, Model?> {
        val dep =
            Dependency().apply {
                this.groupId = groupId
                this.artifactId = artifactId
                this.version = version
            }
        return resolve(dep)
    }
}
