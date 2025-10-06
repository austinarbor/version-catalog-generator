package dev.aga.gradle.versioncatalogs

import java.io.File
import java.nio.file.Paths
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
      classpathFiles.map { it.absolutePath.replace('\\', '/') }.joinToString(",") { "\"$it\"" }
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
                  saveGeneratedCatalog = true
                  from("com.fasterxml.jackson:jackson-bom:2.15.2")
                  using {
                    libraryAliasGenerator = { groupId, artifactId -> 
                      val prefix = aliasPrefixGenerator(groupId, artifactId)
                      val suffix = aliasSuffixGenerator(prefix, groupId, artifactId)
                      GeneratorConfig.DEFAULT_ALIAS_GENERATOR(prefix,suffix)
                    }
                    versionNameGenerator = GeneratorConfig.DEFAULT_VERSION_NAME_GENERATOR
                  }
                }
                generate("mockitoLibs") {
                  saveGeneratedCatalog = true
                  from("org.mockito:mockito-bom:5.5.0") {
                    aliasPrefixGenerator = GeneratorConfig.NO_PREFIX
                    aliasSuffixGenerator = { _, _, artifact ->
                      GeneratorConfig.caseChange(artifact, net.pearx.kasechange.CaseFormat.LOWER_HYPHEN, net.pearx.kasechange.CaseFormat.LOWER_UNDERSCORE)
                    }
                    generateBomEntry = true
                  }
                }
                generate("awsLibs") {
                  fromToml("aws-bom") {
                    aliasPrefixGenerator = GeneratorConfig.NO_PREFIX
                  }
                }
                generate("springLibs") {
                  fromToml("spring-boot-dependencies") {
                    propertyOverrides = mapOf(
                      "jackson-bom.version" to versionRef("jackson")
                    )
                  }
                }
                generate("junitLibs") {
                  saveGeneratedCatalog = true
                  from {
                    toml {
                      libraryAliases = listOf("boms-junit5")
                      file = artifact("io.micronaut.platform:micronaut-platform:4.3.6")
                    }
                  }
                  using {
                    aliasPrefixGenerator = GeneratorConfig.NO_PREFIX
                  }
                }
                generate("manyBoms") {
                  fromToml("spring-boot-dependencies") {
                    propertyOverrides = mapOf(
                      "jackson-bom.version" to versionRef("jackson")
                    )
                  }
                  fromToml("aws-bom", "jackson-bom") {
                    aliasPrefixGenerator = GeneratorConfig.NO_PREFIX
                  }
                }
                // test appending to existing catalog
                generate("libs") {
                  fromToml("aws-bom") {
                    aliasPrefixGenerator = GeneratorConfig.NO_PREFIX
                  }
                }
              }
            }
        """
        .trimIndent()
    )
    buildFile.writeText(
      """
      plugins {
        java
      }
      dependencies {
        implementation(springLibs.spring.springBootStarterWeb)
        implementation(jsonLibs.jackson.jacksonDatabind)
        implementation(jsonLibs.bundles.jacksonModule)
        implementation(awsLibs.s3)
        implementation(manyBoms.spring.springBootStarterJdbc)
        implementation(manyBoms.sts)
        implementation(manyBoms.jacksonDatabind)
        compileOnly(libs.spring.boot.dependencies)
        implementation(libs.s3)
        testImplementation(mockitoLibs.mockito.core)
        testImplementation(mockitoLibs.mockito.junit.jupiter)
        testImplementation(junitLibs.junitJupiter)
        testImplementation(junitLibs.junitJupiterParams)
      }
      """
        .trimIndent()
    )

    versionCatalogFile.writeText(
      """
          [versions]
          aws = "2.21.15"
          jackson = "2.18.2"
          spring = "3.4.1"
          [libraries]
          aws-bom = { group = "software.amazon.awssdk", name = "bom", version.ref = "aws"}
          jackson-bom = { group = "com.fasterxml.jackson", name = "jackson-bom", version.ref = "jackson" }
          spring-boot-dependencies = { group = "org.springframework.boot", name = "spring-boot-dependencies", version.ref = "spring" }
      """
        .trimIndent()
    )

    // Run the build
    val runner =
      GradleRunner.create()
        .forwardOutput()
        .withPluginClasspath()
        .withArguments("--stacktrace")
        .withArguments("clean", "assemble")
        .withProjectDir(projectDir)

    val result = runner.build()

    assertThat(result.output).contains("BUILD SUCCESSFUL")

    assertThat(projectDir.resolve(Paths.get("build", "version-catalogs").toString()))
      .isDirectoryNotContaining { it.name == "awsLibs.versions.toml" }
      .isDirectoryContaining { it.name == "jsonLibs.versions.toml" }
      .isDirectoryContaining { it.name == "junitLibs.versions.toml" }
      .isDirectoryContaining { it.name == "mockitoLibs.versions.toml" }
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
                  it.using { u ->
                    u.libraryAliasGenerator = { groupId, artifactId -> 
                     def prefix = u.aliasPrefixGenerator.invoke(groupId, artifactId)
                     def suffix = u.aliasSuffixGenerator.invoke(prefix, groupId, artifactId)
                     DEFAULT_ALIAS_GENERATOR.invoke(prefix,suffix)
                    }
                    u.versionNameGenerator = DEFAULT_VERSION_NAME_GENERATOR
                  }
                }
                
                generator.generate("mockitoLibs") {
                    it.from("org.mockito:mockito-bom:5.5.0")
                    it.using { u ->
                      u.generateBomEntry = true
                      u.libraryAliasGenerator = { groupId, artifactId -> 
                        def prefix = u.aliasPrefixGenerator.invoke(groupId, artifactId)
                        def suffix = u.aliasSuffixGenerator.invoke(prefix, groupId, artifactId)
                        DEFAULT_ALIAS_GENERATOR.invoke(prefix,suffix)
                      }
                      u.versionNameGenerator = DEFAULT_VERSION_NAME_GENERATOR
                    }
                }
                generator.generate("springLibs") { gen ->
                  gen.fromToml("spring-boot-dependencies")
                  gen.using { u ->
                    u.propertyOverrides = [
                      "jackson-bom.version": u.versionRef("jackson")
                    ]
                  }
                }
                generator.generate("junitLibs") {
                  it.from { from ->
                    from.toml { toml ->
                      toml.libraryAliases = ["boms-junit5"]
                      toml.file = toml.artifact("io.micronaut.platform:micronaut-platform:4.3.6")
                    }
                  }
                  it.using { u ->
                    u.aliasPrefixGenerator = NO_PREFIX
                  }
                }
                generator.generate("manyBoms") {
                  it.from { from ->
                   from.toml { toml ->
                     toml.libraryAliases = ["spring-boot-dependencies"]
                   }
                   from.using { u ->
                    u.propertyOverrides = [
                      "jackson-bom.version": u.versionRef("jackson")
                    ]
                   }
                  }
                  
                  it.from { from ->
                    from.toml { toml ->
                     toml.libraryAliases = ["aws-bom", "jackson-bom"]
                    }
                    from.using { u ->
                      u.aliasPrefixGenerator = NO_PREFIX
                    }
                  }
                }
              }
            }
        """
        .trimIndent()
    )
    buildFile.writeText(
      """
      plugins {
        java
      }
      dependencies {
        implementation(springLibs.spring.springBootStarterWeb)
        implementation(jsonLibs.jackson.jacksonDatabind)
        implementation(jsonLibs.bundles.jacksonModule)
        testImplementation(mockitoLibs.mockito.mockitoCore)
        testImplementation(junitLibs.junitJupiter)
      }
      """
        .trimIndent()
    )

    versionCatalogFile.writeText(
      """
          [versions]
          aws = "2.21.15"
          jackson = "2.18.2"
          spring = "3.4.1"
          [libraries]
          aws-bom = { group = "software.amazon.awssdk", name = "bom", version.ref = "aws"}
          jackson-bom = { group = "com.fasterxml.jackson", name = "jackson-bom", version.ref = "jackson" }
          spring-boot-dependencies = { group = "org.springframework.boot", name = "spring-boot-dependencies", version.ref = "spring" }
      """
        .trimIndent()
    )

    // Run the build
    val runner =
      GradleRunner.create()
        .forwardOutput()
        .withPluginClasspath()
        .withArguments("--stacktrace")
        .withProjectDir(projectDir)

    val result = runner.build()

    assertThat(result.output).contains("BUILD SUCCESSFUL")
  }

  companion object {
    private fun getResourceAsText(name: String): String {
      return VersionCatalogGeneratorPluginTest::class.java.classLoader.getResource(name).readText()
    }
  }
}
