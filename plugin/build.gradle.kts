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

val GROUP_ID: String by project
val ARTIFACT_ID: String by project
val VERSION: String by project
val SCM_URL: String by project
val PLUGIN_DISPLAY_NAME: String by project
val PLUGIN_DESCRIPTION: String by project

group = GROUP_ID
version = VERSION

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

gradlePlugin {
    website = SCM_URL
    vcsUrl = SCM_URL
    val generator by plugins.creating {
        id = "dev.aga.gradle.version-catalog-generator"
        implementationClass = "dev.aga.gradle.versioncatalogs.VersionCatalogGeneratorPlugin"
        displayName = PLUGIN_DISPLAY_NAME
        description = PLUGIN_DESCRIPTION
        tags = listOf("version", "catalog", "bom", "generate")
    }
}

val projectProps = project.properties

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = GROUP_ID
            artifactId = ARTIFACT_ID
            version = VERSION

            pom {
                name = PLUGIN_DISPLAY_NAME
                description = PLUGIN_DESCRIPTION
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
                    url = SCM_URL
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
