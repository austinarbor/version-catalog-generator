package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.assertion.TomlTableAssert
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.dsl.VersionCatalogBuilder.LibraryAliasBuilder
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.tomlj.Toml
import org.tomlj.TomlTable

internal abstract class GeneratorTestBase {

    /** The [Path] to the test resources `src/test/resources` */
    protected val resourceRoot: Path = Paths.get("src", "test", "resources")

    /**
     * A [MutableMap] in which the generated libraries are stored so they can be inspected later.
     * The map is cleared before each test.
     */
    protected val generatedLibraries = mutableMapOf<String, LibraryAliasBuilder>()

    /**
     * A [MutableMap] in which the generated bundles are stored so they can be inspected later. The
     * map is cleared before each test.
     */
    protected val generatedBundles = mutableMapOf<String, List<String>>()

    /**
     * A temporary directory created by Junit which will automatically get cleaned up after each
     * test. The directory is used as the project directory.
     */
    @TempDir protected lateinit var projectDir: File

    /** A mocked instance of [Settings] */
    protected lateinit var settings: Settings

    /**
     * The [VersionCatalogBuilder] on which functions like [VersionCatalogBuilder.library],
     * [VersionCatalogBuilder.version] and [VersionCatalogBuilder.bundle] are called
     */
    protected lateinit var builder: VersionCatalogBuilder

    /**
     * The [MutableVersionCatalogContainer] in which the generated catalog is stored and the
     * function [dev.aga.gradle.versioncatalogs.Generator.generate] is called
     */
    protected lateinit var container: MutableVersionCatalogContainer

    /** A mocked [Gradle] instance */
    protected lateinit var gradle: Gradle

    /** A mocked [Project] instance */
    protected lateinit var project: Project

    /** An [ObjectFactory] from [project] */
    protected lateinit var objectFactory: ObjectFactory

    @BeforeEach
    fun beforeEach() {
        generatedLibraries.clear()
        generatedBundles.clear()

        project = newProject()
        objectFactory = project.objects

        gradle =
            mock<Gradle> {
                doAnswer { invocation ->
                        val a = invocation.arguments[0] as Action<in Gradle>
                        a.execute(it)
                        null
                    }
                    .`when`(it)
                    .projectsEvaluated(any<Action<in Gradle>>())
                on { rootProject } doReturn newProject()
            }
        settings =
            mock<Settings> {
                on { rootDir } doReturn projectDir
                on { gradle } doReturn gradle
            }
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

    protected open fun newProject(): Project =
        ProjectBuilder.builder().withProjectDir(projectDir).build()

    protected open fun verifyGeneratedCatalog(
        config: GeneratorConfig,
        name: String,
        expectedCatalogPath: Path,
    ) {
        verify(container).create(eq(name), any<Action<VersionCatalogBuilder>>())
        val (versions, libraries, bundles) = getExpectedCatalog(expectedCatalogPath)
        verifyVersions(versions)
        verifyLibraries(libraries)
        verifyBundles(bundles)

        if (config.saveGeneratedCatalog) {
            val actual: Path =
                config.saveDirectory.toPath().resolve(Paths.get("${name}.versions.toml"))
            TomlTableAssert.assertThat(actual).isEqualTo(resourceRoot.resolve(expectedCatalogPath))
        }
    }

    protected open fun getExpectedCatalog(tomlPath: Path): Triple<TomlTable, TomlTable, TomlTable> {
        val parseResult = Toml.parse(resourceRoot.resolve(tomlPath))
        val versions = parseResult.getTableOrEmpty("versions")
        val libraries = parseResult.getTableOrEmpty("libraries")
        val bundles = parseResult.getTableOrEmpty("bundles")
        return Triple(versions, libraries, bundles)
    }

    protected open fun verifyLibraries(libraries: TomlTable) {
        verify(builder, times(libraries.size()))
            .library(any<String>(), any<String>(), any<String>())
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

    protected open fun getLibraryAlias(property: String): String {
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

    protected open fun verifyVersions(versions: TomlTable) {
        verify(builder, times(versions.size())).version(any<String>(), any<String>())
        versions.dottedKeySet().forEach { v -> verify(builder).version(v, versions.getString(v)!!) }
    }

    protected open fun verifyBundles(bundles: TomlTable) {
        verify(builder, times(bundles.size())).bundle(any<String>(), any<List<String>>())
        bundles.dottedKeySet().forEach {
            assertThat(generatedBundles).containsKey(it)
            val expectedLibraries = bundles.getArrayOrEmpty(it).toList()
            assertThat(expectedLibraries).containsExactlyInAnyOrderElementsOf(generatedBundles[it])
        }
    }
}
