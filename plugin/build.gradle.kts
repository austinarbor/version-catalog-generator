plugins {
    alias(libs.plugins.kotlin)
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

group = "dev.aga.gradle.plugin"
version = "0.0.1-SNAPSHOT"

gradlePlugin {
    val generator by plugins.creating {
        id = "dev.aga.gradle.plugin.version-catalog-generator"
        implementationClass = "dev.aga.gradle.plugin.versioncatalogs.VersionCatalogGeneratorPlugin"
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "dev.aga.gradle.plugin"
            artifactId = "version-catalog-generator"
            version = "0.0.1-SNAPSHOT"
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
