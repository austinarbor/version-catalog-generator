package dev.aga.gradle.versioncatalogs.service

import dev.aga.gradle.versioncatalogs.exception.ResolutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.attributes.Category
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact
import org.gradle.api.model.ObjectFactory

class GradleDependencyResolver(
    private val objects: ObjectFactory,
    private val drsSupplier: Supplier<DependencyResolutionServices>,
    parentModelResolver: DependencyResolver? = null,
) : DependencyResolver {
    private val drs by lazy { drsSupplier.get() }

    private val parentModelResolver = parentModelResolver ?: this

    override fun resolve(source: Dependency): Pair<Model, Model?> {
        val config = createConfiguration()
        registerDependency(config, source)

        return config.incoming.artifacts.firstOrNull()?.let {
            val path = it.file.toPath()
            val resolver = LocalDependencyResolver(path)
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

            model to parentModel?.first
        }
            ?: throw ResolutionException("Unable to resolve ${source}")
    }

    private fun registerDependency(config: Configuration, source: Dependency) {
        val dep =
            drs.dependencyHandler.create("${source.groupId}:${source.artifactId}:${source.version}")
        when (dep) {
            is ExternalDependency ->
                dep.addArtifact(
                    DefaultDependencyArtifact(
                        dep.name,
                        "pom",
                        "pom",
                        null,
                        null,
                    ),
                )
        }
        config.dependencies.add(dep)
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

    private fun createConfiguration(): Configuration {
        val config =
            drs.configurationContainer.create("incomingBom${configurationCount.incrementAndGet()}")

        return config.apply {
            resolutionStrategy.activateDependencyLocking()
            attributes {
                attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    objects.named(Category::class.java, Category.REGULAR_PLATFORM),
                )
            }
            isCanBeResolved = true
            isCanBeConsumed = false
        }
    }

    companion object {
        private val configurationCount = AtomicInteger(0)
    }
}
