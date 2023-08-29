package dev.aga.gradle.versioncatalogs.service

import java.io.File
import java.nio.file.Paths
import org.apache.maven.model.Dependency
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
    fun testFetch(notation: Any, expectedArtifact: String, shouldThrow: Boolean) {
        val r = LocalDependencyResolver(rootDir)
        if (shouldThrow) {
            assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
                r.resolve(notation)
            }
        } else {
            var model = r.resolve(notation)
            assertThat(model.artifactId).isEqualTo(expectedArtifact)
            if (notation is Dependency) {
                model = r.resolve(notation.groupId, notation.artifactId, notation.version)
                assertThat(model.artifactId).isEqualTo(expectedArtifact)
            }
        }
    }

    companion object {
        @JvmStatic
        fun testFetchProvider(): List<Arguments> {
            val dep =
                Dependency().apply {
                    groupId = "org.assertj"
                    artifactId = "assertj-bom"
                    version = "3.24.2"
                }
            return listOf(
                arguments(File("assertj-bom-3.24.2.pom"), "assertj-bom", false),
                arguments(Paths.get("assertj-bom-3.24.2.pom"), "assertj-bom", false),
                arguments(dep, "assertj-bom", false),
                arguments(Paths.get("not-real.pom"), "", true),
                arguments(Any(), "", true),
            )
        }
    }
}
