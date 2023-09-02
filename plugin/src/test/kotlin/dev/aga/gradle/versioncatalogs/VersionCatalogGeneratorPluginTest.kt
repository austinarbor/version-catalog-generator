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
    private val groovySettingsFile by lazy { projectDir.resolve("settings.gradle") }
    private val propertiesFile by lazy { projectDir.resolve("gradle.properties") }

    private lateinit var classpathString: String

    @BeforeEach
    fun setup() {
        // create the classpath string which we use in the buildscript in the test build file
        val classpathFiles = getResourceAsText("testkit-classpath.txt").lines().map { File(it) }

        classpathString =
            classpathFiles
                .map { it.absolutePath.replace('\\', '/') }
                .joinToString(",") { "\"$it\"" }
        // copy the generated properties file into the runner's directory
        propertiesFile.writeText(getResourceAsText("testkit-gradle.properties"))
    }

    @Test
    fun `kotlin dsl usage succeeds`() {
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
                  libraryAliasGenerator = dev.aga.gradle.versioncatalogs.GeneratorConfig.DEFAULT_ALIAS_GENERATOR
                  versionNameGenerator = dev.aga.gradle.versioncatalogs.GeneratorConfig.DEFAULT_VERSION_NAME_GENERATOR
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

    @Test
    fun `groovy dsl usage succeeds`() {
        // Set up the test build
        groovySettingsFile.writeText(
            """
            import static dev.aga.gradle.versioncatalogs.Generator.INSTANCE as Generator
            
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
                Generator.generate(it, "jsonLibs") {
                  it.from("com.fasterxml.jackson:jackson-bom:2.15.2")
                  it.libraryAliasGenerator = dev.aga.gradle.versioncatalogs.GeneratorConfig.DEFAULT_ALIAS_GENERATOR
                  it.versionNameGenerator = dev.aga.gradle.versioncatalogs.GeneratorConfig.DEFAULT_VERSION_NAME_GENERATOR
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

    companion object {
        private fun getResourceAsText(name: String): String {
            return javaClass.classLoader.getResource(name).readText()
        }
    }
}
