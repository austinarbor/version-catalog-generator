package dev.aga.gradle.versioncatalogs.service

import java.io.File
import org.apache.maven.model.Dependency

interface ArtifactResolver {
    fun resolve(dep: Dependency, type: String = "pom"): File
}
