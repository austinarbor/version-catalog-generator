[![ci](https://github.com/austinarbor/version-catalog-generator/actions/workflows/ci.yml/badge.svg)](https://github.com/austinarbor/version-catalog-generator/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/austinarbor/version-catalog-generator/graph/badge.svg?token=IO5UCDD5A0)](https://codecov.io/gh/austinarbor/version-catalog-generator)
[![Gradle Plugin Portal](https://staging.shields.io/gradle-plugin-portal/v/dev.aga.gradle.version-catalog-generator?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/dev.aga.gradle.version-catalog-generator)

# Version Catalog Generator Plugin

Gradle's [Version Catalog](https://docs.gradle.org/current/userguide/platforms.html) functionality currently lacks
cohesion
with `platform` and `BOM` concepts. The Version Catalog Generator plugin attempts to enhance Gradle by automatically
generating
a version catalog from an external BOM.

## Compatibility
Works on Gradle 7.6+

## Quick Start

Add your BOM dependencies to your version catalog

<details open>
  <summary>libs.versions.toml</summary>

```toml
[versions]
spring = "3.2.0"
aws = "2.22.0"

[libraries]
awsBom = { group = "software.amazon.awssdk", name = "bom", version.ref = "aws" }
springBootDependencies = { group = "org.springframework.boot", name = "spring-boot-dependencies", version.ref = "spring" }
```
</details>

Add the plugin to your settings with the catalogs you want to generate

<details open>
  <summary>settings.gradle.kts</summary>

```kotlin
import dev.aga.gradle.versioncatalogs.Generator.generate

plugins {
  id("dev.aga.gradle.version-catalog-generator") version("1.4.0")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral() // must include repositories here for dependency resolution to work from settings
    }
    versionCatalogs {
        generate("springLibs") { // the name of the generated catalog
            from(toml("springBootDependencies")) // name of the bom library in the version catalog
            propertyOverrides = mapOf(
                "jackson-bom.version" to "2.16.1", // optionally override some version properties using a literal value
                "mockito.version" to versionRef("mockito"), // or you can reference version aliases in the source toml
            )
        }
        generate("awsLibs") { 
            from(toml("awsBom"))
            // all dependencies in the aws bom are for aws so we can skip the prefix
            aliasPrefixGenerator = GeneratorConfig.NO_PREFIX
        }
    }
}
```
</details>

Use the dependencies in your build

<details open>
 <summary>build.gradle.kts</summary>

```kotlin
dependencies {
    implementation(awsLibs.s3)
    implementation(awsLibs.dynamodb)
    implementation(springLibs.spring.springBootStarterWeb)
    implementation(springLibs.jackson.jacksonDatabind)
}
```
</details>

## Detailed Usage
<details>
  <summary>settings.gradle.kts</summary>

```kotlin
import dev.aga.gradle.versioncatalogs.Generator.generate
import dev.aga.gradle.versioncatalogs.GeneratorConfig

plugins {
    id("dev.aga.gradle.version-catalog-generator") version("1.4.0")
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
                    file = file("gradle/libs.versions.toml") // optional, only required if not using this value
                }
            }
            // use this instead if you just want to use direct dependency notation
            from("org.springframework.boot:spring-boot-dependencies:3.1.2")
            // you can optionally change the library alias generation behavior
            // by providing your own algorithms below. check the javadoc for more
            // information
            libraryAliasGenerator = {groupId, artifactId ->
                val prefix = aliasPrefixGenerator(groupId, artifactId)
                val suffix = aliasSuffixGenerator(prefix, groupId, artifactId)
                GeneratorConfig.DEFAULT_ALIAS_GENERATOR(prefix,suffix)
            }
            // for example if you prefer no prefix and camelCase library names you can do:
            aliasPrefixGenerator = GeneratorConfig.NO_PREFIX
            
            // you can optionally change the version alias generation behavior by
            // providing your own algorithm below. check the javadoc for more 
            // information
            versionNameGenerator = GeneratorConfig.DEFAULT_VERSION_NAME_GENERATOR
            
            // you can optionally override version properties from the BOM you are
            // generating a catalog for. for example, if spring-boot-dependencies
            // specifies jackson 2.15.3 but you want to use 2.16.1 instead, you can
            // override the version property in their BOM
            propertyOverrides = mapOf("jackson-bom.version" to "2.16.1")
            
            // you can optionally provide regex patterns to exclude dependencies
            // by their group or name
            excludeGroups = "some\\.group"
            excludeNames = ".*pattern"
            // by default, we will store generated catalogs in build/version-catalogs,
            // relative to the directory in which the settings file is stored. customize that
            // directory by passing in a new value here. A relative directory will be resolved
            // relative to the settings file root. An absolute directory will be used as-is.
            // WARNING: When using a non-standard directory, be cognizant of when this file will
            // get cleaned up (or rather, when it will _not_ . If the directory you use is not
            // cleaned by the clean task, your catalogs will not get updated.
            cacheDirectory = file("build/some-folder")
        }
    }
}
```
</details>
<details>
    <summary>settings.gradle</summary>

```groovy
plugins {
    id('dev.aga.gradle.version-catalog-generator') version '1.4.0'
}

dependencyResolutionManagement {
    repositories {
        mavenCentral() // must include repositories here for dependency resolution to work from settings
    }
    versionCatalogs {
        generator.generate("jsonLibs") {
            it.from("com.fasterxml.jackson:jackson-bom:2.15.2")
            // you can optionally change the library alias generation behavior
            // by providing your own algorithms below. check the javadoc for more
            // information
            it.libraryAliasGenerator = { groupId, artifactId ->
                def prefix = aliasPrefixGenerator.invoke(groupId, artifactId)
                def suffix = aliasSuffixGenerator.invoke(prefix, groupId, artifactId)
                DEFAULT_ALIAS_GENERATOR.invoke(prefix,suffix)
            }
            // you can optionally change the version alias generation behavior by
            // providing your own algorithm below. check the javadoc for more 
            // information
            it.versionNameGenerator = it.DEFAULT_VERSION_NAME_GENERATOR
            
            // you can optionally provide regex patterns to exclude dependencies
            // by their group or name
            excludeGroups = "some\\.group"
            excludeNames = ".*pattern"
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
    implementation(springLibs.spring.springBootStarterJdbc)
}
```
</details>

## Goals

- [x] Compatible with Dependabot
- [x] Nested BOM support (i.e. `spring-boot-dependences` imports `mockito-bom`, etc)
- [x] Easy to override versions (similar to `ext["version.property"] = ...` in Spring Boot Dependencies plugin)
