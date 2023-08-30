package dev.aga.gradle.versioncatalogs.service

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
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
) : DependencyResolver {
    private val drs by lazy { drsSupplier.get() }
    private val count = AtomicInteger(0)

    override fun resolve(notation: Any): Model {
        val config = createConfiguration()
        val dependency = drs.dependencyHandler.create(notation)
        when (dependency) {
            is ExternalDependency ->
                dependency.addArtifact(
                    DefaultDependencyArtifact(
                        dependency.name,
                        "pom",
                        "pom",
                        null,
                        null,
                    ),
                )
        }
        config.dependencies.add(dependency)
        config.incoming.artifacts.first {
            val path = it.file.toPath()
            val resolver = LocalDependencyResolver(path.parent)
            return resolver.resolve(path.fileName)
        }
        throw RuntimeException("Unable to resolve ${notation}")
    }

    private fun createConfiguration(): Configuration {
        val config = drs.configurationContainer.create("incomingBom${count.incrementAndGet()}")
        config.resolutionStrategy.activateDependencyLocking()
        config.attributes {
            attribute(
                Category.CATEGORY_ATTRIBUTE,
                objects.named(Category::class.java, Category.REGULAR_PLATFORM),
            )
        }
        config.isCanBeResolved = true
        config.isCanBeConsumed = false
        return config
    }
}