package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.VersionCatalogGeneratorPluginExtension.Companion.DEFAULT_ALIAS_PREFIX_GENERATOR
import dev.aga.gradle.versioncatalogs.VersionCatalogGeneratorPluginExtension.Companion.DEFAULT_ALIAS_SUFFIX_GENERATOR
import dev.aga.gradle.versioncatalogs.VersionCatalogGeneratorPluginExtension.Companion.DEFAULT_VERSION_NAME_GENERATOR
import dev.aga.gradle.versioncatalogs.VersionCatalogGeneratorPluginExtension.Companion.NO_ALIAS_PREFIX
import net.pearx.kasechange.CaseFormat
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

internal class VersionCatalogGeneratorPluginExtensionTest {
    @ParameterizedTest
    @MethodSource("defaultLibraryNameProvider")
    fun `default library alias generator`(group: String, name: String, expected: String) {
        val actual = VersionCatalogGeneratorPluginExtension.DEFAULT_ALIAS_GENERATOR(group, name)
        assertThat(actual).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("defaultAliasPrefixProvider")
    fun `default alias prefix generator`(groupId: String, artifactId: String, expected: String) {
        if (expected == "error") {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { DEFAULT_ALIAS_PREFIX_GENERATOR(groupId, artifactId) }
                .withMessage(
                    "Cannot generate alias for ${groupId}:${artifactId}, please provide custom generator",
                )
        } else {
            val actual = DEFAULT_ALIAS_PREFIX_GENERATOR(groupId, artifactId)
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
        val actual = DEFAULT_ALIAS_SUFFIX_GENERATOR(prefix, groupId, artifactId)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun testNoAliasPrefix() {
        val actual = NO_ALIAS_PREFIX("whatever", "whatever")
        assertThat(actual).isBlank()
    }

    @ParameterizedTest
    @MethodSource("defaultVersionNameProvider")
    fun `default version name generator`(version: String, expected: String) {
        val actual = DEFAULT_VERSION_NAME_GENERATOR(version)
        assertThat(actual).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("caseChangeProvider")
    fun `case change`(source: String, from: CaseFormat, to: CaseFormat, expected: String) {
        val actual = VersionCatalogGeneratorPluginExtension.caseChange(source, from, to)
        assertThat(actual).isEqualTo(expected)
    }

    companion object {
        @JvmStatic
        private fun defaultLibraryNameProvider(): List<Arguments> {
            return listOf(
                arguments(
                    "",
                    "anything",
                    "anything",
                ),
                arguments(
                    "prefix",
                    "suffix",
                    "prefix-suffix",
                ),
            )
        }

        @JvmStatic
        private fun defaultAliasPrefixProvider(): List<Arguments> {
            return listOf(
                arguments("com.fasterxml.jackson", "any-thing", "jackson"),
                arguments("dev.aga", "version-catalog-generator", "aga"),
                arguments("dev.plugins", "anything", "dev-plugins"),
                arguments("plugins", "anything", "error"),
            )
        }

        @JvmStatic
        private fun defaultAliasSuffixProvider(): List<Arguments> {
            return listOf(
                arguments(
                    "aga",
                    "dev.aga",
                    "version-catalog-generator",
                    "version-catalog-generator",
                ),
                arguments(
                    "aga",
                    "dev.aga",
                    "aga-version-catalog-generator",
                    "version-catalog-generator",
                ),
                arguments(
                    "aga",
                    "dev.aga",
                    "aga",
                    "aga",
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

        @JvmStatic
        private fun caseChangeProvider(): List<Arguments> {
            return listOf(
                arguments("my-module", CaseFormat.LOWER_HYPHEN, CaseFormat.CAMEL, "myModule"),
                arguments("my.group", CaseFormat.LOWER_DOT, CaseFormat.CAMEL, "myGroup"),
            )
        }
    }
}
