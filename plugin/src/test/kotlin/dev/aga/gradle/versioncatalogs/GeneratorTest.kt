package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.Generator.generate
import dev.aga.gradle.versioncatalogs.assertion.TomlTableAssert
import dev.aga.gradle.versioncatalogs.service.MockGradleDependencyResolver
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import org.apache.maven.model.Dependency
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
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

    @TempDir private lateinit var projectDir: File
    private lateinit var settings: Settings
    private lateinit var builder: VersionCatalogBuilder
    private lateinit var container: MutableVersionCatalogContainer
    private lateinit var gradle: Gradle
    private lateinit var project: Project
    private lateinit var objectFactory: ObjectFactory

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

    @ParameterizedTest
    @MethodSource("testGenerateProvider")
    fun testGenerate(
        dep: Dependency,
        cfg: GeneratorConfig.() -> Unit,
        expectedCatalog: Path,
        sourceSet: Boolean = false,
    ) {
        val config =
            GeneratorConfig(settings).apply(cfg).apply {
                if (!sourceSet) {
                    sources.add {
                        GeneratorConfig.SourceConfig(
                            settings,
                            settings.rootDir
                                .toPath()
                                .resolve(Paths.get("gradle", "libs.versions.toml"))
                                .toFile(),
                        ) to listOf(dep)
                    }
                }
                saveDirectory = projectDir
                saveGeneratedCatalog = true
                generateBomEntry = true
            }

        val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
        container.generate("myLibs", config, resolver)
        verify(container).create(eq("myLibs"), any<Action<VersionCatalogBuilder>>())
        val (versions, libraries, bundles) = getExpectedCatalog(expectedCatalog)
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

        val actual = projectDir.resolve("myLibs.versions.toml").toPath()
        val expected = resourceRoot.resolve(expectedCatalog)
        TomlTableAssert.assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun testGenerate_DuplicateAlias() {
        val config =
            GeneratorConfig(settings).apply {
                from("org.springframework.boot:spring-boot-dependencies:2.7.18")
                saveGeneratedCatalog = false
            }
        val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
        assertThatIllegalArgumentException()
            .isThrownBy { container.generate("myLibs", config, resolver) }
            .withMessageContainingAll(
                "Attempting to register a library with the alias ehcache-ehcache",
                "Existing: net.sf.ehcache:ehcache:ehcache",
                "Attempting: org.ehcache:ehcache:ehcache3",
            )
    }

    @Test
    fun testGenerate_InvalidOverrides() {
        val config =
            GeneratorConfig(settings).apply {
                from {
                    toml {
                        libraryAlias = "springBootDependencies"
                        file = Paths.get("src", "test", "resources", "source-toml.toml").toFile()
                    }
                }
                propertyOverrides = mapOf("assertj.version" to versionRef("does-not-exist"))
                saveDirectory = projectDir
            }

        val resolver = MockGradleDependencyResolver(resourceRoot.resolve("poms"))
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            container.generate("myLibs", config, resolver)
        }

        config.propertyOverrides = mapOf("assertj.version" to 1)
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            container.generate("myLibs", config, resolver)
        }
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

    private fun getExpectedCatalog(tomlPath: Path): Triple<TomlTable, TomlTable, TomlTable> {
        val parseResult = Toml.parse(resourceRoot.resolve(tomlPath))
        val versions = parseResult.getTableOrEmpty("versions")
        val libraries = parseResult.getTableOrEmpty("libraries")
        val bundles = parseResult.getTableOrEmpty("bundles")
        return Triple(versions, libraries, bundles)
    }

    private fun newProject(): Project = ProjectBuilder.builder().withProjectDir(projectDir).build()

    companion object {
        @JvmStatic
        fun testGenerateProvider(): List<Arguments> {
            val sb312 = dep("org.springframework.boot", "spring-boot-dependencies", "3.1.2")
            val assertj3242 = dep("org.assertj", "assertj-bom", "3.24.2")

            return listOf(
                createArgs(
                    sb312,
                    "libs.versions.toml",
                ) {},
                createArgs(
                    assertj3242,
                    "name-exclusion-test.toml",
                ) {
                    excludeNames = "^assertj-guava$"
                },
                createArgs(
                    assertj3242,
                    "empty.toml",
                ) {
                    excludeGroups = "org\\.assertj"
                },
                createArgs(
                    assertj3242,
                    "exclusion-negative-test.toml",
                ) {
                    excludeGroups = "org\\.assertj"
                    excludeNames = "xyz"
                },
                createArgs(
                    assertj3242,
                    "both-exclusion-test.toml",
                ) {
                    excludeGroups = "org\\.assertj"
                    excludeNames = "assertj-core"
                },
                createArgs(
                    sb312,
                    "property-overrides.toml",
                ) {
                    propertyOverrides =
                        mapOf(
                            "assertj.version" to "3.25.3",
                            "caffeine.version" to "3.1.8",
                            "jackson-bom.version" to "2.16.1",
                        )
                },
                createArgs(
                    sb312,
                    "property-overrides.toml",
                    true,
                ) {
                    from {
                        toml {
                            libraryAlias = "springBootDependencies"
                            file =
                                Paths.get("src", "test", "resources", "source-toml.toml").toFile()
                        }
                    }
                    propertyOverrides =
                        mapOf(
                            "assertj.version" to versionRef("assertj"),
                            "caffeine.version" to versionRef("caffeine"),
                            "jackson-bom.version" to versionRef("jackson"),
                        )
                },
            )
        }

        private fun createArgs(
            dep: Dependency,
            expectedFileName: String,
            sourceSet: Boolean = false,
            conf: GeneratorConfig.() -> Unit,
        ): Arguments {
            return arguments(dep, conf, expectedPath(dep, expectedFileName), sourceSet)
        }

        private fun expectedPath(dep: Dependency, fileName: String): Path {
            return Paths.get("expectations", dep.artifactId, fileName)
        }

        private fun dep(groupId: String, artifactId: String, version: String): Dependency {
            return Dependency().apply {
                this.groupId = groupId
                this.artifactId = artifactId
                this.version = version
                type = "pom"
            }
        }
    }
}
