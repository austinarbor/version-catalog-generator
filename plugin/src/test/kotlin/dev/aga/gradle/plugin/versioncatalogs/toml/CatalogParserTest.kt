package dev.aga.gradle.plugin.versioncatalogs.toml

import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

internal class CatalogParserTest {
    @ParameterizedTest
    @MethodSource("testFindBomProvider")
    fun testFindBom(libraryName: String, expected: Array<String>, shouldThrow: Boolean = false) {
        val file = buildPath("libs.versions.toml").toFile()
        if (!shouldThrow) {
            val actual = CatalogParser.findBom(file, libraryName)
            assertThat(actual)
                .extracting("groupId", "artifactId", "version")
                .containsExactly(*expected)
        } else {
            assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
                CatalogParser.findBom(file, libraryName)
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
