package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.Generator.generate
import dev.aga.gradle.versioncatalogs.mock.MockVersionCatalogBuilder
import dev.aga.gradle.versioncatalogs.service.MockGradleDependencyResolver
import java.nio.file.Paths
import java.util.TreeSet
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.whenever

internal class GeneratorTest : GeneratorTestBase() {

  @Test
  fun `source config overrides generator config`() {
    val config =
      GeneratorConfig(settings).apply {
        saveDirectory = projectDir
        saveGeneratedCatalog = true
        using {
          aliasPrefixGenerator = { _, _ -> "foo" }
          aliasSuffixGenerator = { _, _, _ -> "bar" }
          versionNameGenerator = { _ -> "foobar" }
          excludeGroups = ".*"
          excludeNames = ".*"
          generateBomEntry = false
          propertyOverrides = mapOf("jackson-bom.version" to "2.16.1")
          generateBomEntryForNestedBoms = false
        }
        from("org.springframework.boot:spring-boot-dependencies:3.1.2") {
          aliasPrefixGenerator = GeneratorConfig.DEFAULT_ALIAS_PREFIX_GENERATOR
          aliasSuffixGenerator = GeneratorConfig.DEFAULT_ALIAS_SUFFIX_GENERATOR
          versionNameGenerator = GeneratorConfig.DEFAULT_VERSION_NAME_GENERATOR
          excludeGroups = ""
          excludeNames = ""
          generateBomEntry = true
          propertyOverrides = emptyMap()
          generateBomEntryForNestedBoms = true
        }
      }
    val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
    container.generate("myLibs", config, resolver)
    val expected = Paths.get("expectations", "spring-boot-dependencies", "libs.versions.toml")
    verifyGeneratedCatalog(config, "myLibs", expected, false)
  }

  @Test
  fun `appends to existing version catalog`() {
    whenever(container.names).thenReturn(TreeSet(setOf("libs")))

    val builder =
      MockVersionCatalogBuilder("libs").apply {
        version("generator", "3.2.2")
        library(
            "version.catalog.generator",
            "dev.aga.gradle.version-catalog-generator",
            "dev.aga.gradle.version-catalog-generator.gradle.plugin",
          )
          .versionRef("generator")
        library("sqlite.jdbc", "dev.aga.sqlite", "sqlite-jdbc").version("3.50.3.0")
        library("commons.lang3", "org.apache.commons", "commons-lang3").version {
          strictly("[3.8, 4.0[")
          prefer("3.9")
        }
        bundle("existing", listOf("sqlite.jdbc", "version.catalog.generator"))
        plugin("shadow", "com.gradleup.shadow").version("9.1.0")
      }

    whenever(container.getByName("libs")).thenReturn(builder)

    val config =
      GeneratorConfig(settings).apply {
        saveDirectory = projectDir
        saveGeneratedCatalog = true
        from("org.springframework.boot:spring-boot-dependencies:3.1.2") { generateBomEntry = true }
      }
    val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
    container.generate("libs", config, resolver)
    val expected = Paths.get("expectations", "spring-boot-dependencies", "appended.toml")
    verifyGeneratedCatalog(
      config,
      "libs",
      expected,
      true,
      listOf("version.catalog.generator", "sqlite.jdbc", "commons.lang3"),
      listOf("generator"),
      listOf("shadow"),
    )
  }

  @Test
  fun `multiple sources and configurations`() {
    val config =
      GeneratorConfig(settings).apply {
        defaultVersionCatalog =
          Paths.get("src", "test", "resources", "tomls", "multiple-sources-test.toml").toFile()
        saveDirectory = projectDir
        saveGeneratedCatalog = true
        using { aliasPrefixGenerator = GeneratorConfig.NO_PREFIX }
        fromToml("spring-boot-dependencies") {
          aliasPrefixGenerator = GeneratorConfig.DEFAULT_ALIAS_PREFIX_GENERATOR
          excludeGroups = "org\\.assertj|software\\.amazon.*"
          propertyOverrides = mapOf("jackson-bom.version" to versionRef("jackson"))
        }
        from("org.assertj:assertj-bom:3.25.3") {
          aliasPrefixGenerator = GeneratorConfig.DEFAULT_ALIAS_PREFIX_GENERATOR
          excludeNames = ".*guava"
        }

        from {
          toml {
            libraryAliases = listOf("junit-bom", "aws-bom")
            file =
              Paths.get("src", "test", "resources", "tomls", "multiple-sources-test.toml").toFile()
          }
          using {
            aliasPrefixGenerator = { groupId, artifactId ->
              if (artifactId.contains("junit")) {
                GeneratorConfig.DEFAULT_ALIAS_PREFIX_GENERATOR(groupId, artifactId)
              } else {
                GeneratorConfig.NO_PREFIX(groupId, artifactId)
              }
            }
            aliasSuffixGenerator = { prefix, groupId, artifactId ->
              GeneratorConfig.DEFAULT_ALIAS_SUFFIX_GENERATOR(
                prefix,
                groupId,
                artifactId.replaceFirst("junit-", ""),
              )
            }
          }
        }
      }
    val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
    container.generate("myLibs", config, resolver)
    val expected = Paths.get("expectations", "combined", "libs.versions.toml")
    verifyGeneratedCatalog(config, "myLibs", expected, false)
  }

  @Test
  fun `default version catalog`() {
    val config =
      GeneratorConfig(settings).apply {
        defaultVersionCatalog =
          Paths.get("src", "test", "resources", "tomls", "source-toml.toml").toFile()
        saveDirectory = projectDir
        saveGeneratedCatalog = true
        fromToml("springBootDependencies") {
          generateBomEntry = true
          propertyOverrides =
            mapOf(
              "jackson-bom.version" to versionRef("jackson"),
              "assertj.version" to versionRef("assertj"),
              "caffeine.version" to "3.1.8",
            )
        }
      }
    val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
    container.generate("myLibs", config, resolver)
    val expected = Paths.get("expectations", "spring-boot-dependencies", "property-overrides.toml")
    verifyGeneratedCatalog(config, "myLibs", expected, false)
  }

  @ParameterizedTest
  @ValueSource(strings = ["does-not-exist"])
  @ValueSource(ints = [1])
  fun `invalid overrides throw exception`(version: Any) {
    val config =
      GeneratorConfig(settings).apply {
        saveDirectory = projectDir
        from {
          toml {
            libraryAliases = listOf("springBootDependencies")
            file = Paths.get("src", "test", "resources", "tomls", "source-toml.toml").toFile()
          }
          val v =
            when (version) {
              is String -> versionRef(version)
              else -> version
            }
          using { propertyOverrides = mapOf("assertj.version" to v) }
        }
      }
    val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
    assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
      container.generate("myLibs", config, resolver)
    }
  }

  @Test
  fun `duplicate aliases throw exception`() {
    val config =
      GeneratorConfig(settings).apply {
        from("org.springframework.boot:spring-boot-dependencies:2.7.18")
        saveGeneratedCatalog = false
      }
    val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
    assertThatIllegalArgumentException()
      .isThrownBy { container.generate("myLibs", config, resolver) }
      .withMessageContainingAll(
        "Attempting to register a library with the alias ehcache-ehcache",
        "Existing: net.sf.ehcache:ehcache:ehcache",
        "Attempting: org.ehcache:ehcache:ehcache3",
      )
  }

  @Test
  fun `custom bundle mapping`() {
    val config =
      GeneratorConfig(settings).apply {
        saveDirectory = projectDir
        saveGeneratedCatalog = true
        from("org.springframework.boot:spring-boot-dependencies:3.1.2")
        bundleMapping = {
          when {
            it.alias.startsWith("caffeine-") -> "caffeine"
            it.alias.startsWith("awssdk-") -> "awssdk"
            else -> null
          }
        }
      }
    val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
    container.generate("myLibs", config, resolver)
    val expected = Paths.get("expectations", "spring-boot-dependencies", "custom-bundles.toml")
    verifyGeneratedCatalog(config, "myLibs", expected, false)
  }

  @Test
  fun `no nested BOMs`() {
    val config =
      GeneratorConfig(settings).apply {
        saveDirectory = projectDir
        saveGeneratedCatalog = true
        from("org.springframework.boot:spring-boot-dependencies:3.1.2")
        using { generateBomEntryForNestedBoms = false }
      }
    val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
    container.generate("myLibs", config, resolver)
    val expected = Paths.get("expectations", "spring-boot-dependencies", "no-nested-boms.toml")
    verifyGeneratedCatalog(config, "myLibs", expected, false)
  }
}
