package dev.aga.gradle.versioncatalogs.service

import dev.aga.gradle.versioncatalogs.exception.ConfigurationException
import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

internal class FileCatalogParserTest {
    @ParameterizedTest
    @MethodSource("testFindBomProvider")
    fun testFindBom(
        libraryName: String,
        expected: Array<String>,
        shouldThrow: Boolean,
        errorContains: String,
    ) {
        val file = buildPath("libs.versions.toml").toFile()
        val parser = FileCatalogParser(file)
        if (!shouldThrow) {
            val actual = parser.findLibrary(libraryName)
            assertThat(actual)
                .extracting("groupId", "artifactId", "version")
                .containsExactly(*expected)
        } else {
            assertThatExceptionOfType(ConfigurationException::class.java)
                .isThrownBy { parser.findLibrary(libraryName) }
                .withMessageContaining(errorContains)
        }
    }

    companion object {

        private const val srcDir = "src/test/resources"

        @JvmStatic
        private fun testFindBomProvider(): List<Arguments> {
            return listOf(
                arguments(
                    "groovy-core",
                    arrayOf("org.codehaus.groovy", "groovy", "3.0.5"),
                    false,
                    "",
                ),
                arguments("fake-lib", arrayOf("dev.aga.lib", "fake-lib", "1.0.2"), false, ""),
                arguments("another-lib", arrayOf("dev.aga.lib", "another-lib", "1.0.0"), false, ""),
                arguments(
                    "commons-lang3",
                    arrayOf(""),
                    true,
                    "Version not found for library commons-lang3 in catalog file",
                ),
                arguments(
                    "missing-ref",
                    arrayOf(""),
                    true,
                    "Version ref 'bad-ref' not found for library missing-ref in catalog file",
                ),
            )
        }

        private fun buildPath(fileName: String) = Paths.get(srcDir, fileName)
    }
}
