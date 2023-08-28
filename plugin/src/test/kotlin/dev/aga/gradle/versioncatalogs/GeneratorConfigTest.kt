package dev.aga.gradle.versioncatalogs

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

internal class GeneratorConfigTest {
    @ParameterizedTest
    @MethodSource("defaultLibraryNameProvider")
    fun `default library alias generator`(group: String, name: String, expected: String) {
        val actual = GeneratorConfig.DEFAULT_ALIAS_GENERATOR(group, name)
        assertThat(actual).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("defaultVersionNameProvider")
    fun `default version name generator`(version: String, expected: String) {
        val actual = GeneratorConfig.DEFAULT_VERSION_NAME_GENERATOR(version)
        assertThat(actual).isEqualTo(expected)
    }

    companion object {
        @JvmStatic
        private fun defaultLibraryNameProvider(): List<Arguments> {
            return listOf(
                arguments("dev.aga", "version-catalog-generator", "aga.version-catalog-generator"),
                arguments(
                    "org.springframework.boot",
                    "spring-boot-starter-web",
                    "boot.spring-boot-starter-web",
                ),
            )
        }

        @JvmStatic
        private fun defaultVersionNameProvider(): List<Arguments> {
            return listOf(
                arguments("activemq.version", "activemq"),
                arguments("jackson.version.modules", "jackson.modules"),
                arguments("devVersion", "dev"),
            )
        }
    }
}
