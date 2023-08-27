![GitHub Workflow Status (with event)](https://img.shields.io/github/actions/workflow/status/austinarbor/version-catalog-generator/.github%2Fworkflows%2Fci.yml)
[![codecov](https://codecov.io/gh/austinarbor/version-catalog-generator/graph/badge.svg?token=IO5UCDD5A0)](https://codecov.io/gh/austinarbor/version-catalog-generator)

# Version Catalog Generator Plugin

Gradle's [Version Catalog](https://docs.gradle.org/current/userguide/platforms.html) functionality currently lacks
cohesion
with `platform` and `BOM` concepts. The Version Catalog Generator plugin attempts to enhance Gradle by automatically
generating
a version catalog from an external BOM.

## Note

This plugin is in alpha! Expect breaking changes until we reach a more stable state.

## Usage

`settings.gradle.kts`

```kotlin
import dev.aga.gradle.plugin.versioncatalogs.Generator.generate

plugins {
    id("dev.aga.gradle.plugin.version-catalog-generator")
}

versionCatalog {
    generate("bomLibs") { // the name of the generated catalog
        sourceLibraryNameInCatalog =
            "spring-boot-dependencies" // required, must be a valid alias in the library file below
        sourceCatalogFile = file("gradle/libs.versions.toml") // optional, change if required
        repoBaseUrl = "https://repo1.maven.org/maven2" // optional, change if required
        libraryAliasGenerator =
            dev.aga.gradle.plugin.versioncatalogs.GeneratorConfig.DEFAULT_ALIAS_GENERATOR // optional, change if required
        versionNameGenerator =
            dev.aga.gradle.plugin.versioncatalogs.GeneratorConfig.DEFAULT_VERSION_NAME_GENERATOR // optional, change if required
    }
}
```

## Goals

- Compatible with Dependabot
- Easy to override versions (similar to `ext["version.property"] = ...` in Spring Boot Dependencies plugin)
- Nested BOM support (i.e. `spring-boot-dependences` imports `mockito-bom`, etc)

## FAQ

### Why must I specify the source BOM in a catalog file?

I really want this to work well with Dependabot. As of this writing, Dependabot only supports
updating dependency versions in Version Catalogs that
are [declared in gradle/libs.versions.toml](https://docs.github.com/en/code-security/dependabot/dependabot-version-updates/about-dependabot-version-updates#gradle).
Although `libs.versions.toml` is the default catalog file, any valid catalog file should work with the plugin. I plan on
adding support for more sources in the future, but this approach will the first priority.

### Why must I specify a Maven repository URL?

`Settings` plugins do not currently have access to the internal Gradle resolution APIs. In `Project` plugins you can
easily query for artifacts, but so far this functionality is not possible from `Settings`.
I [opened an issue](https://github.com/gradle/gradle/issues/26111)
with the Gradle team, but as of this writing it is still awaiting triage. Until that issue is resolved, we unfortunately
must be provided with a Maven repository URL to use to look up the BOM's metadata. If anyone has a workaround for this,
I am definitely interested! We _do_ check the local Gradle cache to see if the pom has already been fetched before
reaching out to the remote URL. 
