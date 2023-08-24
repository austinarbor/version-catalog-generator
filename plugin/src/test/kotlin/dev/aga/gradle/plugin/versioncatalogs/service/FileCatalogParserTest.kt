package dev.aga.gradle.plugin.versioncatalogs.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Paths

internal class FileCatalogParserTest {
    @ParameterizedTest
    @MethodSource("testFindBomProvider")
    fun testFindBom(libraryName: String, expected: Array<String>, shouldThrow: Boolean = false) {
        val file = buildPath("libs.versions.toml").toFile()
        val parser = FileCatalogParser(file)
        if (!shouldThrow) {
            val actual = parser.findLibrary(libraryName)
            assertThat(actual)
                .extracting("groupId", "artifactId", "version")
                .containsExactly(*expected)
        } else {
            assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
                parser.findLibrary(libraryName)
            }
        }
    }

    companion object {

        private const val srcDir = "src/test/resources"

        @JvmStatic
        private fun testFindBomProvider(): List<Arguments> {
            return listOf(
                arguments("groovy-core", arrayOf("org.codehaus.groovy", "groovy", "3.0.5"), false),
                arguments("fake-lib", arrayOf("dev.aga.lib", "fake-lib", "1.0.2"), false),
                arguments("another-lib", arrayOf("dev.aga.lib", "another-lib", "1.0.0"), false),
                arguments("commons-lang3", arrayOf(""), true),
            )
        }

        private fun buildPath(fileName: String) = Paths.get(srcDir, fileName)
    }
}
