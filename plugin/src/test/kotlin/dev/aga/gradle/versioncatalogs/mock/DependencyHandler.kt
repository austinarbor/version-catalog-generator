package dev.aga.gradle.versioncatalogs.mock

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.dsl.ExternalModuleDependencyVariantSpec
import org.gradle.api.artifacts.query.ArtifactResolutionQuery
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.transform.TransformSpec
import org.gradle.api.artifacts.type.ArtifactTypeContainer
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.internal.artifacts.dependencies.DefaultMinimalDependency
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal class DependencyHandler : org.gradle.api.artifacts.dsl.DependencyHandler {
  override fun getExtensions(): ExtensionContainer {
    TODO("Not yet implemented")
  }

  override fun add(
    configurationName: String,
    dependencyNotation: Any,
  ): org.gradle.api.artifacts.Dependency? {
    TODO("Not yet implemented")
  }

  override fun add(
    configurationName: String,
    dependencyNotation: Any,
    configureClosure: Closure<*>,
  ): org.gradle.api.artifacts.Dependency? {
    TODO("Not yet implemented")
  }

  override fun <T : Any?, U : ExternalModuleDependency?> addProvider(
    configurationName: String,
    dependencyNotation: Provider<T>,
    configuration: Action<in U>,
  ) {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> addProvider(configurationName: String, dependencyNotation: Provider<T>) {
    TODO("Not yet implemented")
  }

  override fun <T : Any?, U : ExternalModuleDependency?> addProviderConvertible(
    configurationName: String,
    dependencyNotation: ProviderConvertible<T>,
    configuration: Action<in U>,
  ) {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> addProviderConvertible(
    configurationName: String,
    dependencyNotation: ProviderConvertible<T>,
  ) {
    TODO("Not yet implemented")
  }

  override fun create(dependencyNotation: Any): org.gradle.api.artifacts.Dependency {
    return mock<DefaultMinimalDependency>() { on { name } doReturn "unit-test" }
  }

  override fun create(
    dependencyNotation: Any,
    configureClosure: Closure<*>,
  ): org.gradle.api.artifacts.Dependency {
    TODO("Not yet implemented")
  }

  override fun module(notation: Any): org.gradle.api.artifacts.Dependency {
    TODO("Not yet implemented")
  }

  override fun module(
    notation: Any,
    configureClosure: Closure<*>,
  ): org.gradle.api.artifacts.Dependency {
    TODO("Not yet implemented")
  }

  override fun project(notation: MutableMap<String, *>): org.gradle.api.artifacts.Dependency {
    TODO("Not yet implemented")
  }

  override fun gradleApi(): org.gradle.api.artifacts.Dependency {
    TODO("Not yet implemented")
  }

  override fun gradleTestKit(): org.gradle.api.artifacts.Dependency {
    TODO("Not yet implemented")
  }

  override fun localGroovy(): org.gradle.api.artifacts.Dependency {
    TODO("Not yet implemented")
  }

  override fun getConstraints(): DependencyConstraintHandler {
    TODO("Not yet implemented")
  }

  override fun constraints(configureAction: Action<in DependencyConstraintHandler>) {
    TODO("Not yet implemented")
  }

  override fun getComponents(): ComponentMetadataHandler {
    TODO("Not yet implemented")
  }

  override fun components(configureAction: Action<in ComponentMetadataHandler>) {
    TODO("Not yet implemented")
  }

  override fun getModules(): ComponentModuleMetadataHandler {
    TODO("Not yet implemented")
  }

  override fun modules(configureAction: Action<in ComponentModuleMetadataHandler>) {
    TODO("Not yet implemented")
  }

  override fun createArtifactResolutionQuery(): ArtifactResolutionQuery {
    TODO("Not yet implemented")
  }

  override fun attributesSchema(configureAction: Action<in AttributesSchema>): AttributesSchema {
    TODO("Not yet implemented")
  }

  override fun getAttributesSchema(): AttributesSchema {
    TODO("Not yet implemented")
  }

  override fun getArtifactTypes(): ArtifactTypeContainer {
    TODO("Not yet implemented")
  }

  override fun artifactTypes(configureAction: Action<in ArtifactTypeContainer>) {
    TODO("Not yet implemented")
  }

  override fun <T : TransformParameters?> registerTransform(
    actionType: Class<out TransformAction<T>>,
    registrationAction: Action<in TransformSpec<T>>,
  ) {
    TODO("Not yet implemented")
  }

  override fun platform(notation: Any): org.gradle.api.artifacts.Dependency {
    TODO("Not yet implemented")
  }

  override fun platform(
    notation: Any,
    configureAction: Action<in Dependency>,
  ): org.gradle.api.artifacts.Dependency {
    TODO("Not yet implemented")
  }

  override fun enforcedPlatform(notation: Any): org.gradle.api.artifacts.Dependency {
    TODO("Not yet implemented")
  }

  override fun enforcedPlatform(
    notation: Any,
    configureAction: Action<in Dependency>,
  ): org.gradle.api.artifacts.Dependency {
    TODO("Not yet implemented")
  }

  override fun enforcedPlatform(
    dependencyProvider: Provider<MinimalExternalModuleDependency>
  ): Provider<MinimalExternalModuleDependency> {
    TODO("Not yet implemented")
  }

  override fun testFixtures(notation: Any): org.gradle.api.artifacts.Dependency {
    TODO("Not yet implemented")
  }

  override fun testFixtures(
    notation: Any,
    configureAction: Action<in Dependency>,
  ): org.gradle.api.artifacts.Dependency {
    TODO("Not yet implemented")
  }

  override fun variantOf(
    dependencyProvider: Provider<MinimalExternalModuleDependency>,
    variantSpec: Action<in ExternalModuleDependencyVariantSpec>,
  ): Provider<MinimalExternalModuleDependency> {
    TODO("Not yet implemented")
  }
}
