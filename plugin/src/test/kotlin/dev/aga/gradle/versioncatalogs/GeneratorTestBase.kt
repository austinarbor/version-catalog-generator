package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.assertion.TomlTableAssert
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.text.endsWith
import kotlin.text.split
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.dsl.VersionCatalogBuilder.LibraryAliasBuilder
import org.gradle.api.initialization.dsl.VersionCatalogBuilder.PluginAliasBuilder
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
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
   * A [MutableMap] in which the generated libraries are stored so they can be inspected later. The
   * map is cleared before each test.
   */
  protected val generatedLibraries = mutableMapOf<String, LibraryAliasBuilder>()

  /**
   * A [MutableMap] in which the generated bundles are stored so they can be inspected later. The
   * map is cleared before each test.
   */
  protected val generatedBundles = mutableMapOf<String, List<String>>()

  protected val generatedPlugins = mutableMapOf<String, PluginAliasBuilder>()

  /**
   * A temporary directory created by Junit which will automatically get cleaned up after each test.
   * The directory is used as the project directory.
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
   * The [MutableVersionCatalogContainer] in which the generated catalog is stored and the function
   * [dev.aga.gradle.versioncatalogs.Generator.generate] is called
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
    generatedPlugins.clear()

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
            mock<LibraryAliasBuilder> {
                on { version(any<Action<MutableVersionConstraint>>()) } doAnswer
                  {
                    val action = it.arguments[0] as Action<MutableVersionConstraint>
                    action.execute(DefaultMutableVersionConstraint(""))
                  }
              }
              .also { generatedLibraries[alias] = it }
          }
        on { version(any<String>(), any<String>()) } doAnswer { it.arguments[0] as String }
        on { version(any<String>(), any<Action<MutableVersionConstraint>>()) } doAnswer
          {
            val action = it.arguments[1] as Action<MutableVersionConstraint>
            action.execute(DefaultMutableVersionConstraint(""))
            it.arguments[0] as String
          }
        on { bundle(any<String>(), any<List<String>>()) } doAnswer
          {
            val alias = it.arguments[0] as String
            generatedBundles[alias] = it.arguments[1] as List<String>
            null
          }
        on { plugin(any<String>(), any<String>()) } doAnswer
          { mock ->
            val alias = mock.arguments[0] as String
            mock<PluginAliasBuilder> {
                on { version(any<Action<MutableVersionConstraint>>()) } doAnswer
                  {
                    val action = it.arguments[0] as Action<MutableVersionConstraint>
                    action.execute(DefaultMutableVersionConstraint(""))
                  }
              }
              .also { generatedPlugins[alias] = it }
          }
      }
    container =
      mock<MutableVersionCatalogContainer> {
        on { create(any<String>(), any<Action<VersionCatalogBuilder>>()) }
          .then { mock ->
            (mock.arguments[1] as Action<VersionCatalogBuilder>).execute(builder)
            builder
          }
        on { getByName(any<String>(), any<Action<VersionCatalogBuilder>>()) }
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
    existing: Boolean,
    libraryAliasesFromSource: List<String> = emptyList(),
    versionAliasesFromSource: List<String> = emptyList(),
    pluginAliasesFromSource: List<String> = emptyList(),
  ) {
    if (existing) {
      verify(container).getByName(eq(name))
      verify(container).remove(any<VersionCatalogBuilder>())
    } else {
      verify(container).create(eq(name), any<Action<VersionCatalogBuilder>>())
    }
    val (versions, libraries, bundles, plugins) = getExpectedCatalog(expectedCatalogPath)
    verifyVersions(versions, versionAliasesFromSource)
    verifyLibraries(libraries, libraryAliasesFromSource)
    verifyBundles(bundles, libraryAliasesFromSource)
    if (existing) {
      verifyPlugins(plugins, pluginAliasesFromSource)
    }

    if (config.saveGeneratedCatalog) {
      val actual: Path = config.saveDirectory.toPath().resolve(Paths.get("${name}.versions.toml"))
      TomlTableAssert.assertThat(actual).isEqualTo(resourceRoot.resolve(expectedCatalogPath))
    }
  }

  protected open fun getExpectedCatalog(tomlPath: Path): TomlParseResult {
    val parseResult = Toml.parse(resourceRoot.resolve(tomlPath))
    val versions = parseResult.getTableOrEmpty("versions")
    val libraries = parseResult.getTableOrEmpty("libraries")
    val bundles = parseResult.getTableOrEmpty("bundles")
    val plugins = parseResult.getTableOrEmpty("plugins")
    return TomlParseResult(versions, libraries, bundles, plugins)
  }

  @Suppress("detekt:NestedBlockDepth")
  protected open fun verifyLibraries(libraries: TomlTable, libraryAliasesFromSource: List<String>) {
    // sort the keys and split into groups of 3, which should give us
    // the group, name, and version properties
    libraries
      .dottedKeySet()
      .sorted()
      .groupBy { getLibraryAlias(it) }
      .forEach { (alias, properties) ->
        val usedProps = mutableListOf<String>()
        val group =
          properties
            .first { it.endsWith(".group") }
            .let {
              usedProps += it
              libraries.getString(it)!!
            }
        val name =
          properties
            .first { it.endsWith(".name") }
            .let {
              usedProps += it
              libraries.getString(it)!!
            }
        verify(builder).library(alias, group, name)
        assertThat(generatedLibraries).containsKey(alias)
        val mock = generatedLibraries[alias]!!
        properties
          .filterNot { it in usedProps }
          .forEach { prop ->
            when {
              prop.endsWith(".ref") -> {
                val ref = libraries.getString(prop)!!
                verify(mock).versionRef(ref)
              }
              prop.endsWith(".version") ||
                prop.endsWith(".strictly") ||
                prop.endsWith(".prefer") -> {
                if (alias in libraryAliasesFromSource) {
                  verify(mock).version(any<Action<MutableVersionConstraint>>())
                } else {
                  val value = libraries.getString(prop)!!
                  verify(mock).version(value)
                }
              }
              else -> throw RuntimeException("Unexpected property: ${prop}")
            }
          }
      }
    verify(builder, times(libraries.size())).library(any<String>(), any<String>(), any<String>())
  }

  /**
   * Determines the library alias based on the given property name. To determine the library alias
   * we split the property name by `.` and then remove elements from the end until one of them is
   * _not_ `group`, `name`, `version`, `ref`, `id`, `strictly`, or `prefer`. The remaining strings
   * are then combined back together (in original order) with `.`.
   */
  protected open fun getLibraryAlias(property: String): String {
    return property
      .split(".")
      .reversed()
      .dropWhile { it in listOf("group", "name", "version", "ref", "id", "strictly", "prefer") }
      .reversed()
      .joinToString(".")
  }

  protected open fun verifyVersions(versions: TomlTable, versionAliasesFromSource: List<String>) {
    versions.dottedKeySet().forEach { v ->
      if (v in versionAliasesFromSource) {
        verify(builder).version(eq(v), any<Action<MutableVersionConstraint>>())
      } else {
        verify(builder).version(v, versions.getString(v)!!)
      }
    }
    verify(builder, times(versions.size() - versionAliasesFromSource.size))
      .version(any<String>(), any<String>())
    verify(builder, times(versionAliasesFromSource.size))
      .version(any<String>(), any<Action<MutableVersionConstraint>>())
  }

  protected open fun verifyBundles(bundles: TomlTable, libraryAliasesFromSource: List<String>) {
    bundles.dottedKeySet().forEach {
      verify(builder).bundle(eq(it), any<List<String>>())
      assertThat(generatedBundles).containsKey(it)
      val expectedLibraries = bundles.getArrayOrEmpty(it).toList()
      assertThat(expectedLibraries).containsExactlyInAnyOrderElementsOf(generatedBundles[it])
    }
    verify(builder, times(bundles.size())).bundle(any<String>(), any<List<String>>())
  }

  protected open fun verifyPlugins(plugins: TomlTable, pluginAliasesFromSource: List<String>) {
    // sort the keys and split into groups of 2, which should give us
    // the id and version properties
    plugins
      .dottedKeySet()
      .sorted()
      .groupBy { getLibraryAlias(it) }
      .forEach { (alias, props) ->
        val id = props.first { it.endsWith(".id") }.let { plugins.getString(it)!! }
        verify(builder).plugin(alias, id)
        assertThat(generatedPlugins).containsKey(alias)
        val mock = generatedPlugins[alias]!!
        val versionProp = props.first { it.endsWith(".ref") || it.endsWith(".version") }
        val versionValue = plugins.getString(versionProp)!!

        when {
          versionProp.endsWith(".ref") -> verify(mock).versionRef(versionValue)
          versionProp.endsWith(".version") -> {
            if (alias in pluginAliasesFromSource) {
              verify(mock).version(any<Action<MutableVersionConstraint>>())
            } else {
              verify(mock).version(versionValue)
            }
          }
          else -> throw RuntimeException("Unexpected property: ${versionProp}")
        }
      }
    verify(builder, times(plugins.size())).plugin(any<String>(), any<String>())
  }

  data class TomlParseResult(
    val versions: TomlTable,
    val libraries: TomlTable,
    val bundles: TomlTable,
    val plugins: TomlTable,
  )
}
