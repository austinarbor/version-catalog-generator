plugins {
    alias(libs.plugins.kotlin)
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    jacoco
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

    testImplementation(libs.bundles.testing)
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

tasks.test {
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required = true
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit)
        }
    }
}
