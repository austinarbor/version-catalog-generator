package dev.aga.gradle.versioncatalogs

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class DependenciesTest {
  @Test
  fun testDependency() {
    val dep = Dependency("dev.aga", "version-catalog-generator", "1.0.0")
    with(dep) {
      assertThat(groupId).isEqualTo("dev.aga")
      assertThat(artifactId).isEqualTo("version-catalog-generator")
      assertThat(version).isEqualTo("1.0.0")
    }
  }

  @Test
  fun testToDependency() {
    assertThatIllegalArgumentException()
      .isThrownBy { "x".toDependency() }
      .withMessage(
        "String must contain exactly 3 segments separated by a ':'. Provided string contains 1."
      )

    val dep = "dev.aga:version-catalog-generator:1.0.0".toDependency()
    with(dep) {
      assertThat(groupId).isEqualTo("dev.aga")
      assertThat(artifactId).isEqualTo("version-catalog-generator")
      assertThat(version).isEqualTo("1.0.0")
    }
  }
}
