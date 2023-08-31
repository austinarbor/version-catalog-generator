package dev.aga.gradle.versioncatalogs

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VersionCatalogGeneratorPluginTest {
    @field:TempDir lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle.kts") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }
    private val propertiesFile by lazy { projectDir.resolve("gradle.properties") }

    private lateinit var classpathString: String

    @BeforeEach
    fun setup() {
        val classpathUrl = javaClass.classLoader.getResource("testkit-classpath.txt")
        val classpathFiles = classpathUrl.readText().lines().map { File(it) }
        classpathString =
            classpathFiles
                .map { it.absolutePath.replace('\\', '/') }
                .joinToString(",") { "\"$it\"" }

        propertiesFile.writeText(
            javaClass.classLoader.getResource("testkit-gradle.properties").readText(),
        )
    }

    @Test
    fun `plugin usage succeeds`() {
        // Set up the test build
        settingsFile.writeText(
            """
            import dev.aga.gradle.versioncatalogs.Generator.generate
            buildscript {
              dependencies {
                classpath(files($classpathString))
              }
            }
            plugins {
                id("dev.aga.gradle.version-catalog-generator")
            }
            dependencyResolutionManagement {
              repositories {
                mavenCentral()
              }
              versionCatalogs {
                generate("jsonLibs") {
                  from("com.fasterxml.jackson:jackson-bom:2.15.2")
                }
              }
            }
        """
                .trimIndent(),
        )
        buildFile.writeText(
            """
            plugins {
              java
            }
            dependencies {
              implementation(jsonLibs.core.jackson.databind)
              implementation(jsonLibs.bundles.jackson.module)
            }
            """
                .trimIndent(),
        )

        // Run the build
        val runner =
            GradleRunner.create().forwardOutput().withPluginClasspath().withProjectDir(projectDir)

        val result = runner.build()

        assertThat(result.output).contains("BUILD SUCCESSFUL")
    }
}
