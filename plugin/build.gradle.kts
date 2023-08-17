plugins {
    alias(libs.plugins.kotlin)
    `java-gradle-plugin`
    `kotlin-dsl`
}

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

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit)
        }
    }
}
