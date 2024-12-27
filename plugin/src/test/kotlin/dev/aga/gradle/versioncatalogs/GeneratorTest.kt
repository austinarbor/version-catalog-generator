package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.Generator.generate
import dev.aga.gradle.versioncatalogs.service.MockGradleDependencyResolver
import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

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
                }
                from("org.springframework.boot:spring-boot-dependencies:3.1.2") {
                    using {
                        aliasPrefixGenerator = GeneratorConfig.DEFAULT_ALIAS_PREFIX_GENERATOR
                        aliasSuffixGenerator = GeneratorConfig.DEFAULT_ALIAS_SUFFIX_GENERATOR
                        versionNameGenerator = GeneratorConfig.DEFAULT_VERSION_NAME_GENERATOR
                        excludeGroups = ""
                        excludeNames = ""
                        generateBomEntry = true
                        propertyOverrides = emptyMap()
                    }
                }
            }
        val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
        container.generate("myLibs", config, resolver)
        val expected = Paths.get("expectations", "spring-boot-dependencies", "libs.versions.toml")
        verifyGeneratedCatalog(config, "myLibs", expected)
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
                        file = Paths.get("src", "test", "resources", "source-toml.toml").toFile()
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
}
