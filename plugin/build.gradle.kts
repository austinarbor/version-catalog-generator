import io.gitlab.arturbosch.detekt.Detekt

plugins {
  idea
  alias(libs.plugins.kotlin)
  `kotlin-dsl`
  `java-gradle-plugin`
  `maven-publish`
  jacoco
  alias(libs.plugins.spotless)
  alias(libs.plugins.detekt)
  alias(libs.plugins.gradle.publish)
  alias(libs.plugins.shadow)
  alias(libs.plugins.asciidoctorj)
}

val jacocoRuntime by configurations.creating

repositories { mavenCentral() }

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
  jacocoRuntime(variantOf(libs.jacoco.agent) { classifier("runtime") })

  asciidoctorExtensions(libs.asciidoctor.tabbedCode)
}

jacoco { toolVersion = libs.versions.jacoco.get() }

spotless {
  kotlin {
    ktfmt().googleStyle()
    endWithNewline()
  }
  kotlinGradle {
    ktfmt().googleStyle()
    endWithNewline()
  }
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom("$projectDir/config/detekt.yml")
  baseline = file("$projectDir/config/detekt-baseline.xml")
}

gradlePlugin {
  website = providers.gradleProperty("url")
  vcsUrl = providers.gradleProperty("url")
  val generator by
    plugins.creating {
      id = "dev.aga.gradle.version-catalog-generator"
      implementationClass = "dev.aga.gradle.versioncatalogs.VersionCatalogGeneratorPlugin"
      displayName = providers.gradleProperty("displayName").get()
      description = providers.gradleProperty("description").get()
      tags =
        listOf(
          "version",
          "catalog",
          "generate",
          "bom",
          "pom",
          "dependencies",
          "dependency-management",
        )
    }
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      groupId = "${project.group}"
      artifactId = project.name
      version = "${project.version}"

      pom {
        name = providers.gradleProperty("displayName")
        description = providers.gradleProperty("description")
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
          url = providers.gradleProperty("url")
          connection = url.map { it.replaceFirst("https", "scm:git:git") }
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
  tasks.registering {
    notCompatibleWithConfigurationCache("cannot serialize Gradle script object references")
    val outputDir = file(layout.buildDirectory.dir("testkit"))
    inputs.files(sourceSets.main.get().runtimeClasspath)
    inputs.files(jacocoRuntime)
    outputs.dir(outputDir)
    doLast {
      outputDir.mkdirs()
      val jacocoPath = jacocoRuntime.asPath.replace('\\', '/')
      file("$outputDir/testkit-classpath.txt")
        .writeText(sourceSets.main.get().runtimeClasspath.joinToString("\n"))
      file("$outputDir/testkit-gradle.properties")
        .writeText(
          "org.gradle.jvmargs=-javaagent:${jacocoPath}=destfile=$buildDir/jacoco/test.exec"
        )
    }
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
