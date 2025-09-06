package dev.aga.gradle.versioncatalogs.mock

import java.util.function.Supplier
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder
import org.gradle.api.problems.internal.InternalProblems
import org.gradle.testfixtures.ProjectBuilder
import org.mockito.kotlin.mock

class MockVersionCatalogBuilder(name: String) :
  DefaultVersionCatalogBuilder(
    name,
    MockInterner(),
    MockInterner(),
    ProjectBuilder.builder().build().objects,
    mock<Supplier<DependencyResolutionServices>>(),
  ) {
  override fun getProblemsService(): InternalProblems {
    TODO("Not yet implemented")
  }
}
