[![ci](https://github.com/austinarbor/version-catalog-generator/actions/workflows/ci.yml/badge.svg)](https://github.com/austinarbor/version-catalog-generator/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/austinarbor/version-catalog-generator/graph/badge.svg?token=IO5UCDD5A0)](https://codecov.io/gh/austinarbor/version-catalog-generator)
[![Gradle Plugin Portal](https://staging.shields.io/gradle-plugin-portal/v/dev.aga.gradle.version-catalog-generator?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/dev.aga.gradle.version-catalog-generator)

# Version Catalog Generator Plugin

Gradle's [Version Catalog](https://docs.gradle.org/current/userguide/platforms.html) functionality currently lacks
cohesion
with `platform` and `BOM` concepts. The Version Catalog Generator plugin attempts to enhance Gradle by automatically
generating
a version catalog from an external BOM.

## Note

This plugin is in alpha! Expect breaking changes until we reach a more stable state.

## Usage
<details>
  <summary>settings.gradle.kts</summary>

```kotlin
import dev.aga.gradle.versioncatalogs.Generator.generate

plugins {
    id("dev.aga.gradle.version-catalog-generator") version("0.0.2-alpha")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral() // must include repositories here for dependency resolution to work from settings
    }
    versionCatalogs {
        generate("springLibs") { // the name of the generated catalog
            from {
                toml {
                    libraryAlias = "spring-boot-dependencies" // required, alias of the library in the toml below
                    file = File("gradle/libs.versions.toml") // optional, only required if not using this value
                }
            }
            // use this instead if you just want to use direct dependency notation
            from("org.springframework.boot:spring-boot-dependencies:3.1.2")
            libraryAliasGenerator =
                dev.aga.gradle.versioncatalogs.GeneratorConfig.DEFAULT_ALIAS_GENERATOR // optional, change if required
            versionNameGenerator =
                dev.aga.gradle.versioncatalogs.GeneratorConfig.DEFAULT_VERSION_NAME_GENERATOR // optional, change if required
        }
    }
}
```
</details>
<details>
    <summary>settings.gradle</summary>

```groovy
import static dev.aga.gradle.versioncatalogs.Generator.INSTANCE as Generator

plugins {
    id('dev.aga.gradle.version-catalog-generator') version '0.0.2-alpha'
}

dependencyResolutionManagement {
    repositories {
        mavenCentral() // must include repositories here for dependency resolution to work from settings
    }
    versionCatalogs {
        Generator.generate(it, 'springLibs') {
            it.from("org.springframework.boot:spring-boot-dependencies:3.1.2")
            it.libraryAliasGenerator =
                    dev.aga.gradle.versioncatalogs.GeneratorConfig.DEFAULT_ALIAS_GENERATOR // optional, change if required
            it.versionNameGenerator =
                    dev.aga.gradle.versioncatalogs.GeneratorConfig.DEFAULT_VERSION_NAME_GENERATOR // optional, change if required
        }
    }
}
```
</details>
<details>
    <summary>build.gradle.kts</summary>

```kotlin
// add your dependencies from the generated catalog
dependencies {
    implementation(springLibs.boot.spring.boot.starter.jdbc)
}
```
</details>

## Goals

- [x] Compatible with Dependabot
- [x] Nested BOM support (i.e. `spring-boot-dependences` imports `mockito-bom`, etc)
- [ ] Easy to override versions (similar to `ext["version.property"] = ...` in Spring Boot Dependencies plugin)
