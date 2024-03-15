package dev.aga.gradle.versioncatalogs.service

import org.apache.maven.model.Dependency
import org.apache.maven.model.Model

class GradleDependencyResolver(
    private val artifactResolver: ArtifactResolver,
    parentModelResolver: DependencyResolver? = null,
) : DependencyResolver {

    private val parentModelResolver = parentModelResolver ?: this

    override fun resolve(source: Dependency): Pair<Model, Model?> {
        val fetchedFile = artifactResolver.resolve(source, "pom")
        val resolver = LocalDependencyResolver(fetchedFile.toPath())
        val model = resolver.resolve()
        decorate(source, model)
        val parentDep =
            model.parent?.let { parent ->
                Dependency().apply {
                    groupId = parent.groupId ?: source.groupId
                    artifactId = parent.artifactId
                    version = parent.version ?: source.version
                }
            }

        val parentModel = parentDep?.let { pd -> parentModelResolver.resolve(pd) }

        return model to parentModel?.first
    }

    private fun decorate(source: Dependency, target: Model) {
        target.apply {
            // sometimes the POM files will not be fully populated or will have properties set like
            // ${project.groupId}. Since we have the exact values from the original lookup, set them
            // directly to prevent any complications further down the line
            groupId = source.groupId
            artifactId = source.artifactId
            version = source.version
        }
    }
}
