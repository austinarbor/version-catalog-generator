package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.Generator.generate
import dev.aga.gradle.versioncatalogs.service.MockGradleDependencyResolver
import java.nio.file.Paths
import org.apache.maven.model.Dependency
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Action
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.dsl.VersionCatalogBuilder.LibraryAliasBuilder
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.tomlj.Toml
import org.tomlj.TomlTable

internal class GeneratorTest {

    private val resourceRoot = Paths.get("src", "test", "resources")
    private val generatedLibraries = mutableMapOf<String, LibraryAliasBuilder>()
    private val generatedBundles = mutableMapOf<String, List<String>>()

    @BeforeEach
    fun beforeEach() {
        generatedLibraries.clear()
    }

    @Test
    fun testGenerate() {
        val dep = dep("org.springframework.boot", "spring-boot-dependencies", "3.1.2")
        val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
        val settings = mock<Settings>()
        val config = GeneratorConfig(settings).apply { source = { dep } }

        val builder =
            mock<VersionCatalogBuilder> {
                on { library(any<String>(), any<String>(), any<String>()) } doAnswer
                    { mock ->
                        val alias = mock.arguments[0] as String
                        val mockBuilder = mock<LibraryAliasBuilder>()
                        generatedLibraries[alias] = mockBuilder
                        mockBuilder
                    }
                on { version(any<String>(), any<String>()) } doAnswer { it.arguments[0] as String }
                on { bundle(any<String>(), any<List<String>>()) } doAnswer
                    {
                        val alias = it.arguments[0] as String
                        generatedBundles[alias] = it.arguments[1] as List<String>
                        null
                    }
            }

        val container =
            mock<MutableVersionCatalogContainer> {
                on { create(any<String>(), any<Action<VersionCatalogBuilder>>()) }
                    .then { mock ->
                        (mock.arguments[1] as Action<VersionCatalogBuilder>).execute(builder)
                        builder
                    }
            }
        container.generate("myLibs", config, resolver)
        verify(container).create(eq("myLibs"), any<Action<VersionCatalogBuilder>>())
        val (versions, libraries, bundles) = getExpectedCatalog(dep)
        // validate the versions
        verify(builder, times(21)).version(any<String>(), any<String>())
        versions.dottedKeySet().forEach { v -> verify(builder).version(v, versions.getString(v)!!) }

        verify(builder, times(48)).library(any<String>(), any<String>(), any<String>())
        // sort the keys and split into groups of 3, which should give us
        // the group, name, and version properties
        libraries.dottedKeySet().sorted().chunked(3).forEach { libProps ->
            val alias = getLibraryAlias(libProps[0])
            val group = libraries.getString(libProps[0])!!
            val name = libraries.getString(libProps[1])!!
            assertThat(generatedLibraries).containsKey(alias)
            val mock = generatedLibraries[alias]!!
            val versionProp = libProps[2]
            val versionValue = libraries.getString(versionProp)!!
            verify(builder).library(alias, group, name)

            when {
                versionProp.endsWith(".ref") -> verify(mock).versionRef(versionValue)
                versionProp.endsWith(".version") -> verify(mock).version(versionValue)
                else -> throw RuntimeException("Unexpected property: ${versionProp}")
            }
        }

        verify(builder, times(16)).bundle(any<String>(), any<List<String>>())
        bundles.dottedKeySet().forEach {
            assertThat(generatedBundles.containsKey(it))
            val expectedLibraries = bundles.getArrayOrEmpty(it).toList()
            assertThat(expectedLibraries).containsExactlyInAnyOrderElementsOf(generatedBundles[it])
        }
    }

    private fun getLibraryAlias(property: String): String {
        val split = property.split(".")
        // if we have version.ref, return last -2
        val result = mutableListOf<String>()
        for (s in split) {
            if (s in listOf("name", "group", "version")) {
                break
            }
            result += s
        }
        return result.joinToString(".")
    }

    private fun dep(groupId: String, artifactId: String, version: String): Dependency {
        return Dependency().apply {
            this.groupId = groupId
            this.artifactId = artifactId
            this.version = version
            type = "pom"
        }
    }

    private fun getExpectedCatalog(dep: Dependency): Triple<TomlTable, TomlTable, TomlTable> {
        val expectations = Paths.get("expectations", "${dep.artifactId}", "libs.versions.toml")
        val parseResult = Toml.parse(resourceRoot.resolve(expectations))
        val versions = parseResult.getTable("versions")!!
        val libraries = parseResult.getTable("libraries")!!
        val bundles = parseResult.getTable("bundles")!!
        return Triple(versions, libraries, bundles)
    }
}
