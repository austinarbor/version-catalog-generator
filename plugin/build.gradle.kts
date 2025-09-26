import dev.aga.gradle.versioncatalogs.tasks.CreateTestKitFilesTask
import io.gitlab.arturbosch.detekt.Detekt

plugins {
  idea
  alias(libs.plugins.kotlin)
  `kotlin-dsl`
  `java-gradle-plugin`
  `maven-publish`
  jacoco
  alias(libs.plugins.detekt)
  alias(libs.plugins.gradle.publish)
  alias(libs.plugins.shadow)
  alias(libs.plugins.asciidoctorj)
}

val jacocoRuntime by configurations.creating

val asciidoctorExtensions by configurations.registering

dependencies {
  implementation(libs.maven.model)
  implementation(libs.tomlj)
  implementation(libs.kasechange)
  implementation(libs.commons.text)
  detektPlugins(libs.detekt.formatting)

  testImplementation(libs.bundles.testing)
  testImplementation(libs.bundles.mockito)
  testImplementation(gradleTestKit())
  testRuntimeOnly(files(layout.buildDirectory.dir("testkit")))
  testRuntimeOnly(libs.junitPlatformLauncher)
  jacocoRuntime(variantOf(libs.jacoco.agent) { classifier("runtime") })

  asciidoctorExtensions(libs.asciidoctor.tabbedCode)
}

jacoco { toolVersion = libs.versions.jacoco.get() }

detekt {
  buildUponDefaultConfig = true
  config.setFrom("$projectDir/config/detekt.yml")
  baseline = file("$projectDir/config/detekt-baseline.xml")
}

gradlePlugin {
  website = "https://github.com/austinarbor/version-catalog-generator"
  vcsUrl = "https://github.com/austinarbor/version-catalog-generator.git"
  plugins {
    create("generatorPlugin") {
      id = "dev.aga.gradle.version-catalog-generator"
      implementationClass = "dev.aga.gradle.versioncatalogs.VersionCatalogGeneratorPlugin"
      displayName = "Version Catalog Generator"
      description = "Automatically generate a Gradle version catalog from a Maven BOM"
      tags =
        listOf(
          "version-catalog",
          "generate",
          "bom",
          "pom",
          "dependencies",
          "dependency-management",
          "maven",
        )
    }
  }
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      groupId = "${project.group}"
      artifactId = project.name
      version = "${project.version}"

      pom {
        name = "Version Catalog Generator"
        description = "Automatically generate a Gradle version catalog from a Maven BOM"
        licenses {
          license {
            name = "The Apache Software License, Version 2.0"
            url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            distribution = "repo"
          }
        }
        developers {
          developer {
            id = "austinarbor"
            name = "Austin Arbor"
            email = "aarbor989@gmail.com"
          }
        }
        scm {
          url = "https://github.com/austinarbor/version-catalog-generator"
          connection = "scm:git:git@github.com:austinarbor/version-catalog-generator.git"
        }
      }
      from(components["java"])
    }
  }
}

idea {
  module {
    isDownloadJavadoc = true
    isDownloadSources = true
  }
}

// creates files in build/testkit that we use in
// VersionCatalogGeneratorPluginTest to instrument
// the Testkit runner with jacoco so we get test
// coverage output
val createTestkitFiles by
  tasks.registering(CreateTestKitFilesTask::class) {
    jacocoClasspath = jacocoRuntime.asPath
    runtimeClasspath.from(sourceSets.main.get().runtimeClasspath)
  }

tasks {
  shadowJar { archiveClassifier = "" }
  withType<Detekt>().configureEach {
    // exclude the mock classes from detekt
    exclude("dev/aga/gradle/versioncatalogs/mock/**")
  }
  test {
    dependsOn(createTestkitFiles)
    finalizedBy(jacocoTestReport) // report is always generated after tests run
    useJUnitPlatform()
  }
  jacocoTestReport {
    dependsOn(test)
    reports { xml.required = true }
  }
  asciidoctor {
    notCompatibleWithConfigurationCache("AsciidoctorTask not compatible with configuration cache")
    configurations(asciidoctorExtensions)
    attributes =
      mapOf(
        "version" to project.version,
        "revnumber" to "${project.version}",
        "rootdir" to rootDir.absolutePath,
        "author" to "Austin G. Arbor",
        "source-highlighter" to "prettify",
      )
    baseDirFollowsSourceDir()
  }
}
