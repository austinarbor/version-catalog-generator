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
            import dev.aga.gradle.versioncatalogs.VersionCatalogGeneratorPluginExtension
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
                  libraryAliasGenerator = { groupId, artifactId -> 
                    val prefix = aliasPrefixGenerator(groupId, artifactId)
                    val suffix = aliasSuffixGenerator(prefix, groupId, artifactId)
                    VersionCatalogGeneratorPluginExtension.DEFAULT_ALIAS_GENERATOR(prefix,suffix)
                  }
                  versionNameGenerator = VersionCatalogGeneratorPluginExtension.DEFAULT_VERSION_NAME_GENERATOR
                }
                generate("mockitoLibs") {
                  from("org.mockito:mockito-bom:5.5.0")
                  aliasPrefixGenerator = VersionCatalogGeneratorPluginExtension.NO_ALIAS_PREFIX
                  aliasSuffixGenerator = { _, _, artifact ->
                    VersionCatalogGeneratorPluginExtension.caseChange(artifact, net.pearx.kasechange.CaseFormat.LOWER_HYPHEN, net.pearx.kasechange.CaseFormat.CAMEL)
                  }
                }
                generate("junitLibs") {
                  from("org.junit:junit-bom:5.10.0")
                  libraryAliasGenerator = VersionCatalogGeneratorPluginExtension.CAMEL_CASE_NAME_LIBRARY_ALIAS_GENERATOR
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
              implementation(jsonLibs.jackson.databind)
              implementation(jsonLibs.bundles.jackson.module)
              testImplementation(mockitoLibs.mockitoCore)
              testImplementation(mockitoLibs.mockitoJunitJupiter)
              testImplementation(junitLibs.junitJupiter)
              testImplementation(junitLibs.junitJupiterParams)
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
                generator.generate("jsonLibs") {
                  it.from("com.fasterxml.jackson:jackson-bom:2.15.2")
                  it.libraryAliasGenerator = { groupId, artifactId -> 
                   def prefix = aliasPrefixGenerator.invoke(groupId, artifactId)
                   def suffix = aliasSuffixGenerator.invoke(prefix, groupId, artifactId)
                   DEFAULT_ALIAS_GENERATOR.invoke(prefix,suffix)
                  }
                  it.versionNameGenerator = it.DEFAULT_VERSION_NAME_GENERATOR
                }
                
                generator.generate("mockitoLibs") {
                    it.from("org.mockito:mockito-bom:5.5.0")
                    it.libraryAliasGenerator = { groupId, artifactId -> 
                      def prefix = aliasPrefixGenerator.invoke(groupId, artifactId)
                      def suffix = aliasSuffixGenerator.invoke(prefix, groupId, artifactId)
                      DEFAULT_ALIAS_GENERATOR.invoke(prefix,suffix)
                    }
                    it.versionNameGenerator = it.DEFAULT_VERSION_NAME_GENERATOR
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
              implementation(jsonLibs.jackson.databind)
              implementation(jsonLibs.bundles.jackson.module)
              testImplementation(mockitoLibs.mockito.core)
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
