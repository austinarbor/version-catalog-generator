package dev.aga.gradle.plugin.versioncatalogs

import dev.aga.gradle.plugin.versioncatalogs.Generator.generate
import dev.aga.gradle.plugin.versioncatalogs.service.CatalogParser
import dev.aga.gradle.plugin.versioncatalogs.service.LocalPOMFetcher
import org.apache.maven.model.Dependency
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Action
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.dsl.VersionCatalogBuilder.LibraryAliasBuilder
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

// in the mocks, add created libraries, bundles, versions to lists etc that we can more easily
// verify later

internal class GeneratorTest {

    private val createdVersions = mutableMapOf<String, String>()
    private val libraries = mutableMapOf<String, Triple<String, String, String>>()
    private val libraryRefs = mutableMapOf<String, Triple<String, String, String>>()

    @BeforeEach
    fun setup() {
        createdVersions.clear()
        libraries.clear()
        libraryRefs.clear()
    }

    @Test
    fun testGenerate() {
        val dep = dep("org.springframework.boot", "spring-boot-abbrev-dependencies", "3.1.2")
        val parser = mock<CatalogParser> { on { findLibrary(anyString()) } doReturn dep }
        val fetcher = LocalPOMFetcher("src/test/resources/poms")

        val builder =
            mock<VersionCatalogBuilder> {
                on { library(any<String>(), any<String>(), any<String>()) } doAnswer
                    { mock ->
                        val alias = mock.arguments[0] as String
                        val group = mock.arguments[1] as String
                        val name = mock.arguments[2] as String
                        mock<LibraryAliasBuilder> {
                            on { version(any<String>()) } doAnswer
                                {
                                    val dep = Triple(group, name, it.arguments[0] as String)
                                    libraries.put(alias, dep)
                                    null
                                }
                            on { versionRef(any<String>()) } doAnswer
                                {
                                    val dep = Triple(group, name, it.arguments[0] as String)
                                    libraryRefs.put(alias, dep)
                                    null
                                }
                        }
                    }
                on { version(any<String>(), any<String>()) } doAnswer
                    {
                        createdVersions[it.arguments[0] as String] = it.arguments[1] as String
                        it.arguments[0] as String
                    }
            }

        val container =
            mock<MutableVersionCatalogContainer> {
                on { create(anyString(), any<Action<VersionCatalogBuilder>>()) }
                    .then { mock ->
                        (mock.arguments[1] as Action<VersionCatalogBuilder>).execute(builder)
                        builder
                    }
            }
        container.generate("myLibs", GeneratorConfig(), parser, fetcher)
        verify(container).create(eq("myLibs"), any<Action<VersionCatalogBuilder>>())
        assertThat(createdVersions).containsExactlyInAnyOrderEntriesOf(expectedVersions())
    }

    private fun dep(groupId: String, artifactId: String, version: String): Dependency {
        return Dependency().apply {
            this.groupId = groupId
            this.artifactId = artifactId
            this.version = version
            type = "pom"
        }
    }

    private fun expectedVersions(): Map<String, String> {
        return mapOf(
            "activemq" to "5.18.2",
            "assertj" to "3.24.2",
            "brave" to "5.15.1",
            "caffeine" to "3.1.6",
            "dropwizard-metrics" to "4.2.19",
            "jackson" to "2.15.2",
            "jackson.annotations" to "2.15.2",
            "jackson-bom" to "2.15.2",
            "jackson.core" to "2.15.2",
            "jackson.databind" to "2.15.2",
            "jackson.dataformat" to "2.15.2",
            "jackson.datatype" to "2.15.2",
            "jackson.jaxrs" to "2.15.2",
            "jackson.jacksonjr" to "2.15.2",
            "jackson.jakarta.rs" to "2.15.2",
            "jackson.module" to "2.15.2",
            "jackson.module.kotlin" to "2.15.2",
            "jackson.module.scala" to "2.15.2",
            "javax.activation" to "1.2.0",
            "main.basedir" to "\${project.basedir}/..",
            "nexus-staging-maven-plugin" to "1.6.8",
            "project.build.outputEncoding" to "UTF-8",
            "project.build.outputTimestamp" to "2023-05-30T20:28:33Z",
            "project.build.resourceEncoding" to "UTF-8",
            "project.build.sourceEncoding" to "UTF-8",
            "project.reporting.outputEncoding" to "UTF-8",
            "zipkin" to "2.23.2",
            "zipkin-proto3" to "1.0.0",
            "zipkin-reporter" to "2.16.3",
        )
    }
}
