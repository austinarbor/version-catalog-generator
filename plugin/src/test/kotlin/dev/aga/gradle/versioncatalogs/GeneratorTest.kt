package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.Generator.generate
import dev.aga.gradle.versioncatalogs.service.MockGradleDependencyResolver
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import org.apache.maven.model.Dependency
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Action
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.dsl.VersionCatalogBuilder.LibraryAliasBuilder
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable

internal class GeneratorTest {

    private val resourceRoot = Paths.get("src", "test", "resources")
    private val generatedLibraries = mutableMapOf<String, LibraryAliasBuilder>()
    private val generatedBundles = mutableMapOf<String, List<String>>()

    @TempDir private lateinit var projectDir: File
    private lateinit var settings: Settings
    private lateinit var builder: VersionCatalogBuilder
    private lateinit var container: MutableVersionCatalogContainer

    @BeforeEach
    fun beforeEach() {
        generatedLibraries.clear()
        generatedBundles.clear()

        settings = mock<Settings> { on { rootDir } doReturn projectDir }
        builder =
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
        container =
            mock<MutableVersionCatalogContainer> {
                on { create(any<String>(), any<Action<VersionCatalogBuilder>>()) }
                    .then { mock ->
                        (mock.arguments[1] as Action<VersionCatalogBuilder>).execute(builder)
                        builder
                    }
            }
    }

    @Test
    fun testGenerate() {
        val dep = dep("org.springframework.boot", "spring-boot-dependencies", "3.1.2")
        val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
        val config = GeneratorConfig(settings).apply { source = { dep } }

        container.generate("myLibs", objectFactory, config, resolver)
        verify(container).create(eq("myLibs"), any<Action<VersionCatalogBuilder>>())
        val (versions, libraries, bundles) = getExpectedCatalog(dep)
        // validate the versions
        verify(builder, times(versions.size())).version(any<String>(), any<String>())
        versions.dottedKeySet().forEach { v -> verify(builder).version(v, versions.getString(v)!!) }

        verify(builder, times(libraries.size()))
            .library(any<String>(), any<String>(), any<String>())
        verifyLibraries(libraries)

        verify(builder, times(bundles.size())).bundle(any<String>(), any<List<String>>())
        bundles.dottedKeySet().forEach {
            assertThat(generatedBundles).containsKey(it)
            val expectedLibraries = bundles.getArrayOrEmpty(it).toList()
            assertThat(expectedLibraries).containsExactlyInAnyOrderElementsOf(generatedBundles[it])
        }

        assertTomlTableEquals("myLibs", dep)
    }

    @Test
    fun testGenerate_ExcludeByName() {
        val dep = dep("org.assertj", "assertj-bom", "3.24.2")
        val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
        val config =
            GeneratorConfig(settings).apply {
                source = { dep }
                excludeNames = "^assertj-guava$"
            }

        container.generate("myLibs", objectFactory, config, resolver)
        verify(container).create(eq("myLibs"), any<Action<VersionCatalogBuilder>>())
        val (_, libraries, _) =
            getExpectedCatalog(Paths.get("expectations", "assertj-bom", "name-exclusion-test.toml"))

        verify(builder, times(0)).version(any<String>(), any<String>())
        verify(builder, times(1)).library(any<String>(), any<String>(), any<String>())
        verifyLibraries(libraries)
        verify(builder, times(0)).bundle(any<String>(), any<List<String>>())

        assertTomlTableEquals(
            "myLibs",
            "3.24.2",
            Paths.get("expectations", "assertj-bom", "name-exclusion-test.toml"),
        )
    }

    @Test
    fun testGenerate_ExcludeByGroup() {
        val dep = dep("org.assertj", "assertj-bom", "3.24.2")
        val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
        val config =
            GeneratorConfig(settings).apply {
                source = { dep }
                excludeGroups = "org\\.assertj"
            }

        container.generate("myLibs", objectFactory, config, resolver)
        verify(container).create(eq("myLibs"), any<Action<VersionCatalogBuilder>>())
        verify(builder, times(0)).version(any<String>(), any<String>())
        verify(builder, times(0)).library(any<String>(), any<String>(), any<String>())
        verify(builder, times(0)).bundle(any<String>(), any<List<String>>())
    }

    @Test
    fun testGenerate_ExcludeByBoth_Negative() {
        val dep = dep("org.assertj", "assertj-bom", "3.24.2")
        val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
        val config =
            GeneratorConfig(settings).apply {
                source = { dep }
                excludeGroups = "org\\.assertj"
                excludeNames = "xyz"
            }

        container.generate("myLibs", objectFactory, config, resolver)
        verify(container).create(eq("myLibs"), any<Action<VersionCatalogBuilder>>())
        val (_, libraries, _) =
            getExpectedCatalog(
                Paths.get("expectations", "assertj-bom", "exclusion-negative-test.toml"),
            )
        verify(builder, times(0)).version(any<String>(), any<String>())
        verify(builder, times(2)).library(any<String>(), any<String>(), any<String>())
        verifyLibraries(libraries)
        verify(builder, times(0)).bundle(any<String>(), any<List<String>>())
        assertTomlTableEquals(
            "myLibs",
            "3.24.2",
            Paths.get("expectations", "assertj-bom", "exclusion-negative-test.toml"),
        )
    }

    @Test
    fun testGenerate_ExcludeByBoth_Positive() {
        val dep = dep("org.assertj", "assertj-bom", "3.24.2")
        val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
        val config =
            GeneratorConfig(settings).apply {
                source = { dep }
                excludeGroups = "org\\.assertj"
                excludeNames = "assertj-core"
            }

        container.generate("myLibs", objectFactory, config, resolver)
        verify(container).create(eq("myLibs"), any<Action<VersionCatalogBuilder>>())
        val (_, libraries, _) =
            getExpectedCatalog(Paths.get("expectations", "assertj-bom", "both-exclusion-test.toml"))
        verify(builder, times(0)).version(any<String>(), any<String>())
        verify(builder, times(1)).library(any<String>(), any<String>(), any<String>())
        verifyLibraries(libraries)
        verify(builder, times(0)).bundle(any<String>(), any<List<String>>())
        assertTomlTableEquals(
            "myLibs",
            "3.24.2",
            Paths.get("expectations", "assertj-bom", "both-exclusion-test.toml"),
        )
    }

    private fun verifyLibraries(libraries: TomlTable) {
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
    }

    private fun assertTomlArrayEquals(actual: TomlArray, expected: TomlArray) {
        assertThat(actual.size()).isEqualTo(expected.size())
        for (i in 0 until actual.size()) {
            assertThat(actual[i]).isEqualTo(expected[i])
        }
    }

    private fun assertTomlTableEquals(name: String, dep: Dependency) {
        val cachedLib =
            settings.rootDir
                .toPath()
                .resolve(Paths.get("build", "catalogs", "libs.${name}-${dep.version}.toml"))
        assertThat(cachedLib).exists()

        val cachedToml = Toml.parse(cachedLib)
        val expectedToml = getExpectedToml(dep)
        assertTomlTableEquals(cachedToml, expectedToml)
    }

    private fun assertTomlTableEquals(name: String, version: String, expectedPath: Path) {
        val cachedLib =
            settings.rootDir
                .toPath()
                .resolve(Paths.get("build", "catalogs", "libs.${name}-${version}.toml"))
        assertThat(cachedLib).exists()

        val cachedToml = Toml.parse(cachedLib)
        val expectedToml = getExpectedToml(expectedPath)
        assertTomlTableEquals(cachedToml, expectedToml)
    }

    private fun assertTomlTableEquals(actual: TomlTable, expected: TomlTable) {
        assertThat(actual.size()).isEqualTo(expected.size())
        assertThat(actual.dottedKeySet())
            .containsExactlyInAnyOrderElementsOf(expected.dottedKeySet())

        actual.dottedKeySet().forEach { key ->
            when (val value = actual.get(key)) {
                is TomlArray -> assertTomlArrayEquals(value, expected.getArray(key)!!)
                is TomlTable -> assertTomlTableEquals(value, expected.getTable(key)!!)
                else -> assertThat(value).isEqualTo(expected.get(key))
            }
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

    private fun getExpectedToml(dep: Dependency): TomlParseResult {
        val path = Paths.get("expectations", "${dep.artifactId}", "libs.versions.toml")
        return Toml.parse(resourceRoot.resolve(path))
    }

    private fun getExpectedToml(tomlPath: Path): TomlParseResult {
        return Toml.parse(resourceRoot.resolve(tomlPath))
    }

    private fun getExpectedCatalog(dep: Dependency): Triple<TomlTable, TomlTable, TomlTable> {
        val parseResult = getExpectedToml(dep)
        val versions = parseResult.getTableOrEmpty("versions")
        val libraries = parseResult.getTableOrEmpty("libraries")
        val bundles = parseResult.getTableOrEmpty("bundles")
        return Triple(versions, libraries, bundles)
    }

    private fun getExpectedCatalog(tomlPath: Path): Triple<TomlTable, TomlTable, TomlTable> {
        val parseResult = Toml.parse(resourceRoot.resolve(tomlPath))
        val versions = parseResult.getTableOrEmpty("versions")
        val libraries = parseResult.getTableOrEmpty("libraries")
        val bundles = parseResult.getTableOrEmpty("bundles")
        return Triple(versions, libraries, bundles)
    }

    private val objectFactory: ObjectFactory = ProjectBuilder.builder().build().objects
}
