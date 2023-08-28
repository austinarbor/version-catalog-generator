import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlin)
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.gradle.publish)
    alias(libs.plugins.shadow)
}
val projectProps = project.properties
val mavenGroupId = projectProps["GROUP_ID"].toString()
val mavenArtifactId = projectProps["ARTIFACT_ID"].toString()
val mavenVersion = projectProps["VERSION"].toString()

group = mavenGroupId
version = mavenVersion

gradlePlugin {
    website = projectProps["SCM_URL"].toString()
    vcsUrl = projectProps["SCM_URL"].toString()
    val generator by plugins.creating {
        id = "dev.aga.gradle.version-catalog-generator"
        implementationClass = "dev.aga.gradle.versioncatalogs.VersionCatalogGeneratorPlugin"
        displayName = projectProps["PLUGIN_DISPLAY_NAME"].toString()
        description = projectProps["PLUGIN_DESCRIPTION"].toString()
        tags = listOf("version", "catalog", "bom", "generate")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.maven.model)
    implementation(libs.tomlj)

    detektPlugins(libs.detekt.formatting)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.bundles.mockito)
}

spotless {
    kotlin {
        ktfmt().dropboxStyle()
        endWithNewline()
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$projectDir/config/detekt.yml")
    baseline = file("$projectDir/config/detekt-baseline.xml")
}


tasks.withType<ShadowJar> {
    archiveClassifier = ""
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = mavenGroupId
            artifactId = mavenArtifactId
            version = mavenVersion

            pom {
                name = projectProps["PLUGIN_DISPLAY_NAME"].toString()
                description = projectProps["PLUGIN_DESCRIPTION"].toString()
                licenses {
                    license {
                        name = projectProps["LICENSE_NAME"].toString()
                        url = projectProps["LICENSE_URL"].toString()
                        distribution = projectProps["LICENSE_DISTRIBUTION"].toString()
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
                    url = projectProps["SCM_URL"].toString()
                    connection = projectProps["SCM_CONNECTION"].toString()
                }
            }

            from(components["java"])
        }
    }
}

tasks {
    test {
        finalizedBy(jacocoTestReport) // report is always generated after tests run
    }
    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.required = true
        }
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit)
        }
    }
}
