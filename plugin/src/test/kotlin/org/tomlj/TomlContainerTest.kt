package org.tomlj

import dev.aga.gradle.versioncatalogs.assertion.TomlTableAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class TomlContainerTest {
  @Test
  fun `getLibrary throws IllegalArgumentException when alias is missing`() {
    val container = TomlContainer()
    assertThatIllegalArgumentException().isThrownBy { container.getLibrary("anything") }
  }

  @Test
  fun testGetLibraryVersionString() {
    val container = TomlContainer()
    container.addLibrary(
      "my-plugin1",
      "dev.aga.gradle",
      "version-catalog-generator",
      "1.4.1",
      false,
    )
    assertThat(container.getLibraryVersionString("my-plugin1")).isEqualTo("1.4.1")

    container.addLibrary(
      "my-plugin2",
      "dev.aga.gradle",
      "version-catalog-generator",
      "plugin",
      true,
    )
    assertThat(container.getLibraryVersionString("my-plugin2")).isEqualTo("plugin")
  }

  @TestFactory
  fun `addLibrary with various version types`(): List<DynamicTest> {
    val tests =
      listOf(
        Triple("simple version", createVersion("", "", "1.0.0", null, emptyList()), "1.0.0"),
        Triple(
          "rich version",
          createVersion("3.9", "[3.8, 4.0[", "", "main", emptyList()),
          createTomlTable("3.9", "[3.8, 4.0[", "", "main", emptyList()),
        ),
        Triple(
          "rejected",
          createVersion("3.9", "", "", null, listOf("3.8")),
          createTomlTable("3.9", "", "", null, listOf("3.8")),
        ),
      )

    return tests.map { (name, version, expected) ->
      DynamicTest.dynamicTest(name) {
        val container =
          TomlContainer().apply { addLibrary("sqlite", "dev.aga.sqlite", "sqlite-jdbc", version) }

        when (val actual = container.getLibrary("sqlite").get("version")) {
          is TomlTable -> TomlTableAssert.assertThat(actual).isEqualTo(expected as TomlTable)
          else -> assertThat(actual).isEqualTo(expected)
        }
        val lib = container.first()
        if (version.strictVersion.isNotBlank()) {
          assertThat(lib.version).isEqualTo(version.strictVersion)
        } else if (version.requiredVersion.isNotBlank()) {
          assertThat(lib.version).isEqualTo(version.requiredVersion)
        } else if (version.preferredVersion.isNotBlank()) {
          assertThat(lib.version).isEqualTo(version.preferredVersion)
        }
      }
    }
  }

  @Test
  fun getVersionAliases() {
    val container = TomlContainer()
    container.addVersion("a", "1")
    container.addVersion("a-b", "2")
    container.addVersion("a-b-c", "3")
    container.addVersion("b.c", "4")
    container.addVersion("d.e.f", "5")
    assertThat(container.getVersionAliases())
      .containsExactlyInAnyOrder("a", "a-b", "a-b-c", "b.c", "d.e.f")
  }

  private fun createVersion(
    preferred: String,
    strict: String,
    required: String,
    branch: String?,
    rejected: List<String>,
  ): ImmutableVersionConstraint {
    return DefaultImmutableVersionConstraint(preferred, required, strict, rejected, branch)
  }

  private fun createTomlTable(
    preferred: String,
    strict: String,
    required: String,
    branch: String?,
    rejected: List<String>,
  ): TomlTable {
    val pos = TomlPosition.positionAt(1, 1)
    return MutableTomlTable(TomlVersion.LATEST).apply {
      required.takeIf { it.isNotBlank() && it != strict }?.also { set("require", it, pos) }
      strict.takeIf { it.isNotBlank() }?.also { set("strictly", it, pos) }
      preferred.takeIf { it.isNotBlank() }?.also { set("prefer", it, pos) }
      branch?.takeIf { it.isNotBlank() }?.also { set("branch", it, pos) }
      rejected
        .takeIf { it.isNotEmpty() }
        ?.also {
          val arr =
            MutableTomlArray(false).apply {
              for (r in rejected) {
                append(r, pos)
              }
            }
          set("reject", arr, pos)
        }
    }
  }
}
