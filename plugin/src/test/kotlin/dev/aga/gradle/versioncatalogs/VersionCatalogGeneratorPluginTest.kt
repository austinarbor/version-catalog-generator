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
    private val versionCatalogFile by lazy {
        projectDir.resolve("gradle").resolve("libs.versions.toml")
    }

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

        versionCatalogFile.parentFile.mkdirs()
    }

    @Test
    fun `kotlin dsl usage succeeds`() {
        // Set up the test build
        settingsFile.writeText(
            """
            import dev.aga.gradle.versioncatalogs.Generator.generate
            import dev.aga.gradle.versioncatalogs.GeneratorConfig
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
                    GeneratorConfig.DEFAULT_ALIAS_GENERATOR(prefix,suffix)
                  }
                  versionNameGenerator = GeneratorConfig.DEFAULT_VERSION_NAME_GENERATOR
                }
                generate("mockitoLibs") {
                  from("org.mockito:mockito-bom:5.5.0")
                  aliasPrefixGenerator = GeneratorConfig.NO_ALIAS_PREFIX
                  aliasSuffixGenerator = { _, _, artifact ->
                    GeneratorConfig.caseChange(artifact, net.pearx.kasechange.CaseFormat.LOWER_HYPHEN, net.pearx.kasechange.CaseFormat.LOWER_UNDERSCORE)
                  }
                }
                generate("junitLibs") {
                  from("org.junit:junit-bom:5.10.0")
                  aliasPrefixGenerator = GeneratorConfig.NO_ALIAS_PREFIX
                }
                generate("awsLibs") {
                  from(toml("aws-bom"))
                  aliasPrefixGenerator = GeneratorConfig.NO_ALIAS_PREFIX
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
              implementation(jsonLibs.jackson.jacksonDatabind)
              implementation(jsonLibs.bundles.jacksonModule)
              implementation(awsLibs.s3)
              testImplementation(mockitoLibs.mockito.core)
              testImplementation(mockitoLibs.mockito.junit.jupiter)
              testImplementation(junitLibs.junitJupiter)
              testImplementation(junitLibs.junitJupiterParams)
            }
            """
                .trimIndent(),
        )

        versionCatalogFile.writeText(
            """
                [versions]
                aws = "2.21.15"
                [libraries]
                aws-bom = { group = "software.amazon.awssdk", name = "bom", version.ref = "aws"}
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
              implementation(jsonLibs.jackson.jacksonDatabind)
              implementation(jsonLibs.bundles.jacksonModule)
              testImplementation(mockitoLibs.mockito.mockitoCore)
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
            return VersionCatalogGeneratorPluginTest::class
                .java
                .classLoader
                .getResource(name)
                .readText()
        }
    }
}
