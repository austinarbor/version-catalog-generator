import dev.aga.gradle.versioncatalogs.Generator.generate
import dev.aga.gradle.versioncatalogs.GeneratorConfig

pluginManagement {
  repositories {
    mavenLocal()
    gradlePluginPortal()
  }
}

plugins { id("dev.aga.gradle.version-catalog-generator") version "3.2.2-SNAPSHOT" }

dependencyResolutionManagement {
  repositories { mavenCentral() }
  versionCatalogs {
    create("myLibs") { from(files("gradle/catalog.versions.toml")) }
    // test appending to existing catalog
    generate("myLibs") {
      fromToml("aws-bom") { aliasPrefixGenerator = GeneratorConfig.NO_PREFIX }
      bundleMapping = {
        if (it.alias == "sts" || it.alias == "commons-csv") {
          "merged"
        } else {
          it.versionRef
        }
      }
    }

    generate("jsonLibs") {
      saveGeneratedCatalog = true
      from("com.fasterxml.jackson:jackson-bom:2.15.2")
      using {
        libraryAliasGenerator = { groupId, artifactId ->
          val prefix = aliasPrefixGenerator(groupId, artifactId)
          val suffix = aliasSuffixGenerator(prefix, groupId, artifactId)
          GeneratorConfig.DEFAULT_ALIAS_GENERATOR(prefix, suffix)
        }
        versionNameGenerator = GeneratorConfig.DEFAULT_VERSION_NAME_GENERATOR
      }
    }
    generate("mockitoLibs") {
      saveGeneratedCatalog = true
      from("org.mockito:mockito-bom:5.5.0") {
        aliasPrefixGenerator = GeneratorConfig.NO_PREFIX
        aliasSuffixGenerator = { _, _, artifact ->
          GeneratorConfig.caseChange(
            artifact,
            net.pearx.kasechange.CaseFormat.LOWER_HYPHEN,
            net.pearx.kasechange.CaseFormat.LOWER_UNDERSCORE,
          )
        }
        generateBomEntry = true
      }
    }
    generate("awsLibs") { fromToml("aws-bom") { aliasPrefixGenerator = GeneratorConfig.NO_PREFIX } }
    generate("springLibs") {
      fromToml("spring-boot-dependencies") {
        propertyOverrides = mapOf("jackson-bom.version" to versionRef("jackson"))
      }
    }
    generate("junitLibs") {
      saveGeneratedCatalog = true
      from {
        toml {
          libraryAliases = listOf("boms-junit5")
          file = artifact("io.micronaut.platform:micronaut-platform:4.3.6")
        }
      }
      using { aliasPrefixGenerator = GeneratorConfig.NO_PREFIX }
    }
    generate("manyBoms") {
      fromToml("spring-boot-dependencies") {
        propertyOverrides = mapOf("jackson-bom.version" to versionRef("jackson"))
      }
      fromToml("aws-bom", "jackson-bom") { aliasPrefixGenerator = GeneratorConfig.NO_PREFIX }
    }
  }
}
