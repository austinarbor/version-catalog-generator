package dev.aga.gradle.versioncatalogs.service

import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class LocalDependencyResolverTest {
    private val rootDir = Paths.get("src", "test", "resources", "poms")

    @ParameterizedTest
    @MethodSource("testFetchProvider")
    fun testFetch(fileName: String, expectedArtifact: String, shouldThrow: Boolean) {
        val path = rootDir.resolve(fileName)
        val r = LocalDependencyResolver(path)
        if (shouldThrow) {
            assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
                r.resolve()
            }
        } else {
            with(r.resolve()) { assertThat(artifactId).isEqualTo(expectedArtifact) }
        }
    }

    companion object {
        @JvmStatic
        fun testFetchProvider(): List<Arguments> {
            return listOf(
                arguments("assertj-bom-3.24.2.pom", "assertj-bom", false),
                arguments("assertj-bom-3.24.2.pom", "assertj-bom", false),
                arguments("not-real.pom", "", true),
            )
        }
    }
}
