package dev.aga.gradle.versioncatalogs.service

import dev.aga.gradle.versioncatalogs.exception.ResolutionException
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
import org.apache.maven.model.Dependency
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.attributes.Category
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact
import org.gradle.api.model.ObjectFactory

class PublishedArtifactResolver(
    private val objects: ObjectFactory,
    private val drsSupplier: Supplier<DependencyResolutionServices>,
) : ArtifactResolver {
    private val drs by lazy { drsSupplier.get() }

    override fun resolve(dep: Dependency, type: String): File {
        val config = createConfiguration()
        registerDependency(config, dep, type)
        return config.incoming.artifacts.firstOrNull()?.file
            ?: throw ResolutionException("Unable to resolve ${dep} with type ${type}")
    }

    private fun registerDependency(config: Configuration, source: Dependency, type: String) {
        val dep =
            drs.dependencyHandler.create("${source.groupId}:${source.artifactId}:${source.version}")
        when (dep) {
            is ExternalDependency ->
                dep.addArtifact(
                    DefaultDependencyArtifact(
                        dep.name,
                        type,
                        type,
                        null,
                        null,
                    ),
                )
        }
        config.dependencies.add(dep)
    }

    private fun createConfiguration(): Configuration {
        val config =
            drs.configurationContainer.create(
                "incomingConfiguration${configurationCount.incrementAndGet()}",
            )

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
