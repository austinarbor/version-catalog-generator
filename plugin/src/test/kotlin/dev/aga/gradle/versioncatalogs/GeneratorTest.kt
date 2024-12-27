package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.Generator.generate
import dev.aga.gradle.versioncatalogs.assertion.TomlTableAssert
import dev.aga.gradle.versioncatalogs.service.MockGradleDependencyResolver
import java.nio.file.Path
import java.nio.file.Paths
import org.apache.maven.model.Dependency
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.times

internal class GeneratorTest : GeneratorTestBase() {

    @ParameterizedTest
    @MethodSource("testGenerateProvider")
    fun testGenerate(
        dep: Dependency,
        cfg: GeneratorConfig.() -> Unit,
        expectedCatalog: Path,
        sourceSet: Boolean = false,
    ) {
        val config =
            GeneratorConfig(settings).apply(cfg).apply {
                if (!sourceSet) {
                    sources.add {
                        GeneratorConfig.SourceConfig(
                            settings,
                            settings.rootDir
                                .toPath()
                                .resolve(Paths.get("gradle", "libs.versions.toml"))
                                .toFile(),
                        ) to listOf(dep)
                    }
                }
                saveDirectory = projectDir
                saveGeneratedCatalog = true
                generateBomEntry = true
            }

        val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
        container.generate("myLibs", config, resolver)

        verifyGeneratedCatalog("myLibs", expectedCatalog)

        val actual = projectDir.resolve("myLibs.versions.toml").toPath()
        val expected = resourceRoot.resolve(expectedCatalog)
        TomlTableAssert.assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun testGenerate_DuplicateAlias() {
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
    fun testGenerate_InvalidOverrides() {
        val config =
            GeneratorConfig(settings).apply {
                from {
                    toml {
                        libraryAlias = "springBootDependencies"
                        file = Paths.get("src", "test", "resources", "source-toml.toml").toFile()
                    }
                }
                propertyOverrides = mapOf("assertj.version" to versionRef("does-not-exist"))
                saveDirectory = projectDir
            }

        val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            container.generate("myLibs", config, resolver)
        }

        config.propertyOverrides = mapOf("assertj.version" to 1)
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            container.generate("myLibs", config, resolver)
        }
    }

    companion object {
        @JvmStatic
        fun testGenerateProvider(): List<Arguments> {
            val sb312 = dep("org.springframework.boot", "spring-boot-dependencies", "3.1.2")
            val assertj3242 = dep("org.assertj", "assertj-bom", "3.24.2")

            return listOf(
                createArgs(
                    sb312,
                    "libs.versions.toml",
                ) {},
                createArgs(
                    assertj3242,
                    "name-exclusion-test.toml",
                ) {
                    excludeNames = "^assertj-guava$"
                },
                createArgs(
                    assertj3242,
                    "empty.toml",
                ) {
                    excludeGroups = "org\\.assertj"
                },
                createArgs(
                    assertj3242,
                    "exclusion-negative-test.toml",
                ) {
                    excludeGroups = "org\\.assertj"
                    excludeNames = "xyz"
                },
                createArgs(
                    assertj3242,
                    "both-exclusion-test.toml",
                ) {
                    excludeGroups = "org\\.assertj"
                    excludeNames = "assertj-core"
                },
                createArgs(
                    sb312,
                    "property-overrides.toml",
                ) {
                    propertyOverrides =
                        mapOf(
                            "assertj.version" to "3.25.3",
                            "caffeine.version" to "3.1.8",
                            "jackson-bom.version" to "2.16.1",
                        )
                },
                createArgs(
                    sb312,
                    "property-overrides.toml",
                    true,
                ) {
                    from {
                        toml {
                            libraryAlias = "springBootDependencies"
                            file =
                                Paths.get("src", "test", "resources", "source-toml.toml").toFile()
                        }
                    }
                    propertyOverrides =
                        mapOf(
                            "assertj.version" to versionRef("assertj"),
                            "caffeine.version" to versionRef("caffeine"),
                            "jackson-bom.version" to versionRef("jackson"),
                        )
                },
            )
        }

        private fun createArgs(
            dep: Dependency,
            expectedFileName: String,
            sourceSet: Boolean = false,
            conf: GeneratorConfig.() -> Unit,
        ): Arguments {
            return arguments(dep, conf, expectedPath(dep, expectedFileName), sourceSet)
        }

        private fun expectedPath(dep: Dependency, fileName: String): Path {
            return Paths.get("expectations", dep.artifactId, fileName)
        }

        private fun dep(groupId: String, artifactId: String, version: String): Dependency {
            return Dependency().apply {
                this.groupId = groupId
                this.artifactId = artifactId
                this.version = version
                type = "pom"
            }
        }
    }
}
