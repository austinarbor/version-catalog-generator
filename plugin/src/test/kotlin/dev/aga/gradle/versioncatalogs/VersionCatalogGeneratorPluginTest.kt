package dev.aga.gradle.versioncatalogs

import java.io.File
import org.assertj.core.api.Assertions
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VersionCatalogGeneratorPluginTest {
    @field:TempDir lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle.kts") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }

    @Test
    fun `plugin usage succeeds`() {
        // Set up the test build
        settingsFile.writeText(
            """
            import dev.aga.gradle.versioncatalogs.Generator.generate
            
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
                .trimIndent())
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
                .trimIndent())

        // Run the build
        val runner =
            GradleRunner.create().forwardOutput().withPluginClasspath().withProjectDir(projectDir)

        val result = runner.build()

        Assertions.assertThat(result.output).contains("BUILD SUCCESSFUL")
        // Verify the result
        // assertTrue(result.output.contains("Hello from plugin
        // 'dev.aga.gradle.plugin.versioncatalogs.greeting'"))
    }
}
