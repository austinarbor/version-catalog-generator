package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.GeneratorConfig.Companion.DEFAULT_ALIAS_GENERATOR
import java.io.File
import java.nio.file.Paths
import net.pearx.kasechange.CaseFormat
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.gradle.api.initialization.Settings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class GeneratorConfigTest {
  @ParameterizedTest
  @MethodSource("defaultLibraryNameProvider")
  fun `default library alias generator`(group: String, name: String, expected: String) {
    val actual = DEFAULT_ALIAS_GENERATOR(group, name)
    assertThat(actual).isEqualTo(expected)
  }

  @ParameterizedTest
  @MethodSource("defaultAliasPrefixProvider")
  fun `default alias prefix generator`(groupId: String, artifactId: String, expected: String) {
    if (expected == "error") {
      assertThatExceptionOfType(IllegalArgumentException::class.java)
        .isThrownBy { GeneratorConfig.DEFAULT_ALIAS_PREFIX_GENERATOR(groupId, artifactId) }
        .withMessage(
          "Cannot generate alias for ${groupId}:${artifactId}, please provide custom generator"
        )
    } else {
      val actual = GeneratorConfig.DEFAULT_ALIAS_PREFIX_GENERATOR(groupId, artifactId)
      assertThat(actual).isEqualTo(expected)
    }
  }

  @ParameterizedTest
  @MethodSource("defaultAliasSuffixProvider")
  fun `default alias suffix generator`(
    prefix: String,
    groupId: String,
    artifactId: String,
    expected: String,
  ) {
    val actual = GeneratorConfig.DEFAULT_ALIAS_SUFFIX_GENERATOR(prefix, groupId, artifactId)
    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun testNoAliasPrefix() {
    val actual = GeneratorConfig.NO_PREFIX("whatever", "whatever")
    assertThat(actual).isBlank()
  }

  @ParameterizedTest
  @MethodSource("defaultVersionNameProvider")
  fun `default version name generator`(version: String, expected: String) {
    val actual = GeneratorConfig.DEFAULT_VERSION_NAME_GENERATOR(version)
    assertThat(actual).isEqualTo(expected)
  }

  @ParameterizedTest
  @MethodSource("caseChangeProvider")
  fun `case change`(source: String, from: CaseFormat, to: CaseFormat, expected: String) {
    val actual = GeneratorConfig.caseChange(source, from, to)
    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `save dir`() {
    val rootPath = Paths.get("/path", "to", "root")
    val settings = mock<Settings> { on { rootDir } doReturn rootPath.toFile() }
    val config = GeneratorConfig(settings)
    var expected = rootPath.resolve(Paths.get("build", "version-catalogs")).toFile()
    assertThat(config.saveDirectory).isEqualTo(expected)

    val absolute = File("/some/path")
    config.saveDirectory = absolute
    assertThat(config.saveDirectory).isEqualTo(absolute)

    val relative = File("some/path")
    config.saveDirectory = relative
    expected = rootPath.toFile().resolve(relative)
    assertThat(config.saveDirectory).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource("'     ',''", ".*,.*", ",''")
  fun `exclude groups setter only sets non blank strings`(
    toSet: String?,
    expected: String,
    @TempDir tmp: File,
  ) {
    val settings = mock<Settings> { on { rootDir } doReturn tmp }
    val config = GeneratorConfig(settings).apply { excludeGroups = toSet }
    assertThat(config.usingConfig.excludeGroups).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource("'     ',''", ".*,.*", ",''")
  fun `exclude names setter only sets non blank strings`(
    toSet: String?,
    expected: String,
    @TempDir tmp: File,
  ) {
    val settings = mock<Settings> { on { rootDir } doReturn tmp }
    val config = GeneratorConfig(settings).apply { excludeNames = toSet }
    assertThat(config.usingConfig.excludeNames).isEqualTo(expected)
  }

  @Test
  fun `filter overrides exclude groups and names`(@TempDir tmp: File) {
    val settings = mock<Settings> { on { rootDir } doReturn tmp }
    val config =
      GeneratorConfig(settings).apply {
        from {
          using {
            filter = { true }
            excludeNames = ".*"
            excludeGroups = ".*"
          }
        }
      }
    assertThat(config.usingConfig.excludeFilter(Dependency("a", "b", "c"))).isFalse
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = ["a", "b"])
  fun `TomlConfig libraryAlias cannot be set to null`(toSet: String?) {
    val cfg = GeneratorConfig.TomlConfig(mock<Settings>(), mock<File>())
    if (toSet == null) {
      assertThatIllegalArgumentException().isThrownBy { cfg.libraryAlias = toSet }
    } else {
      cfg.libraryAlias = toSet
      assertThat(cfg.libraryAlias).isEqualTo(toSet)
    }
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = [true, false])
  fun `generateBomEntry in using block cannot be set to null`(toSet: Boolean?, @TempDir tmp: File) {
    val settings = mock<Settings> { on { rootDir } doReturn tmp }
    if (toSet == null) {
      assertThatIllegalArgumentException().isThrownBy {
        GeneratorConfig(settings).apply { using { generateBomEntry = toSet } }
      }
    } else {
      val config = GeneratorConfig(settings).apply { using { generateBomEntry = toSet } }
      assertThat(config.usingConfig.generateBomEntry).isEqualTo(toSet)
    }
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = [true, false])
  fun `generateBomEntryForNestedBoms in using block cannot be set to null`(
    toSet: Boolean?,
    @TempDir tmp: File,
  ) {
    val settings = mock<Settings> { on { rootDir } doReturn tmp }
    if (toSet == null) {
      assertThatIllegalArgumentException().isThrownBy {
        GeneratorConfig(settings).apply { using { generateBomEntryForNestedBoms = toSet } }
      }
    } else {
      val config =
        GeneratorConfig(settings).apply { using { generateBomEntryForNestedBoms = toSet } }
      assertThat(config.usingConfig.generateBomEntryForNestedBoms).isEqualTo(toSet)
    }
  }

  companion object {
    @JvmStatic
    private fun defaultLibraryNameProvider(): List<Arguments> {
      return listOf(
        arguments("", "anything", "anything"),
        arguments("prefix", "suffix", "prefix-suffix"),
      )
    }

    @JvmStatic
    private fun defaultAliasPrefixProvider(): List<Arguments> {
      return listOf(
        arguments("com.fasterxml.jackson", "any-thing", "jackson"),
        arguments("tools.jackson", "any-thing", "jackson3"),
        arguments("com.oracle.database.jdbc", "ojdbc8", "oracle"),
        arguments("com.google.android.material", "material", "android"),
        arguments("com.facebook.react", "react", "facebook"),
        arguments("org.springframework.boot", "spring-boot-starter-web", "spring"),
        arguments("org.hibernate.orm", "hibernate-core", "hibernate"),
        arguments("org.apache.httpcomponents.client5", "httpclient5", "httpcomponents"),
        arguments("org.apache.tomcat.embed", "tomcat-embed-core", "tomcat"),
        arguments("org.eclipse.jetty.ee10", "jetty-ee10-bom", "jetty"),
        arguments("org.elasticsearch.client", "elasticsearch-rest-client", "elasticsearch"),
        arguments("org.firebirdsql.jdbc", "jaybird", "firebird"),
        arguments("org.glassfish.jersey.core", "jersey-client", "jersey"),
        arguments("org.jetbrains.kotlinx", "anything", "kotlinx"),
        arguments("org.jetbrains.kotlin", "anything", "kotlin"),
        arguments("org.junit.jupiter", "junit-jupiter-api", "junit"),
        arguments("org.mariadb.jdbc", "mariadb-java-client", "mariadb"),
        arguments("org.mockito.kotlin", "mockito-kotlin", "mockito"),
        arguments("org.neo4j.build", "advanced-build", "neo4j"),
        arguments("io.projectreactor.rabbitmq", "reactor-rabbitmq", "projectreactor"),
        arguments("io.zipkin.brave", "brave", "zipkin"),
        arguments("io.dropwizard.metrics", "metrics", "dropwizard"),
        arguments("jakarta.activation", "jakarta.activation-api", "jakarta"),
        arguments("commons-io", "commons-io", "commons"),
        arguments("commons-lang", "commons-lang", "commons"),
        arguments("commons-codec", "commons-codec", "commons"),
        arguments("commons-logging", "commons-logging", "commons"),
        arguments("commons-collections", "commons-collections", "commons"),
        arguments("androidx.appcompat", "appcompat", "androidx"),
        arguments("dev.aga", "version-catalog-generator", "aga"),
        arguments("dev.plugins", "anything", "devPlugins"),
        arguments("plugins", "anything", "error"),
      )
    }

    @JvmStatic
    private fun defaultAliasSuffixProvider(): List<Arguments> {
      return listOf(
        arguments("aga", "dev.aga", "version-catalog-generator", "versionCatalogGenerator"),
        arguments("aga", "dev.aga", "aga-version-catalog-generator", "agaVersionCatalogGenerator"),
        arguments("aga", "dev.aga", "aga", "aga"),
        arguments(
          "spring",
          "org.springframework.boot",
          "spring-boot-starter-web",
          "springBootStarterWeb",
        ),
        arguments("spring", "org.springframework", "spring-web", "springWeb"),
        arguments(
          "jakarta",
          "jakarta.persistence",
          "jakarta.persistence-api",
          "jakartaPersistenceApi",
        ),
      )
    }

    @JvmStatic
    private fun defaultVersionNameProvider(): List<Arguments> {
      return listOf(
        arguments("activemq.version", "activemq"),
        arguments("jackson.version.modules", "jacksonModules"),
        arguments("devVersion", "dev"),
      )
    }

    @JvmStatic
    private fun caseChangeProvider(): List<Arguments> {
      return listOf(
        arguments("my-module", CaseFormat.LOWER_HYPHEN, CaseFormat.CAMEL, "myModule"),
        arguments("my.group", CaseFormat.LOWER_DOT, CaseFormat.CAMEL, "myGroup"),
      )
    }
  }
}
