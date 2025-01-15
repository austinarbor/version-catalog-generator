package dev.aga.gradle.versioncatalogs.service

import dev.aga.gradle.versioncatalogs.mock.ArtifactCollection
import dev.aga.gradle.versioncatalogs.mock.ArtifactResult
import dev.aga.gradle.versioncatalogs.mock.DependencyHandler
import java.nio.file.Path
import java.util.function.Supplier
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.dsl.DependencyHandler as GradleDependencyHandler
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.dependencies.DefaultMinimalDependency
import org.gradle.api.model.ObjectFactory
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MockGradleDependencyResolver(val rootDir: Path) : DependencyResolver {
  private val delegate: GradleDependencyResolver
  private val incoming = mock<ResolvableDependencies>()
  private val configuration =
    mock<Configuration> {
      on { resolutionStrategy } doReturn mock<ResolutionStrategy>()
      on { attributes } doReturn mock<AttributeContainer>()
      on { dependencies } doReturn mock<DependencySet>()
      on { incoming } doReturn incoming
    }

  private val configurationContainer =
    mock<ConfigurationContainer> { on { detachedConfiguration() } doReturn configuration }

  private val drs =
    mock<DependencyResolutionServices> {
      on { configurationContainer } doReturn configurationContainer
      on { dependencyHandler } doReturn DependencyHandler()
    }

  private val dependencyHandler =
    mock<GradleDependencyHandler> { on { create(any()) } doReturn mock<DefaultMinimalDependency>() }
  private val objectFactory = mock<ObjectFactory>()

  init {
    val supp: Supplier<DependencyResolutionServices> = Supplier { drs }
    val artifactResolver = PublishedArtifactResolver(objectFactory, supp)
    delegate = GradleDependencyResolver(artifactResolver, this)
  }

  override fun resolve(source: Dependency): Pair<Model, Model?> {
    val result = ArtifactResult(rootDir, source)
    val collection = ArtifactCollection(result)
    whenever(incoming.artifacts).doReturn(collection)
    return delegate.resolve(source)
  }
}
