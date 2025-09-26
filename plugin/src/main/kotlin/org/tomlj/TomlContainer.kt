package org.tomlj

import dev.aga.gradle.versioncatalogs.model.GeneratedLibrary
import org.gradle.api.artifacts.VersionConstraint

@Suppress("detekt:TooManyFunctions")
class TomlContainer : Iterable<GeneratedLibrary> {
  private val oneOne = TomlPosition.positionAt(1, 1)
  private val toml: MutableTomlTable = MutableTomlTable(TomlVersion.LATEST)
  private val versions: MutableTomlTable = MutableTomlTable(TomlVersion.LATEST)
  private val libraries: MutableTomlTable = MutableTomlTable(TomlVersion.LATEST)
  private val bundles: MutableTomlTable = MutableTomlTable(TomlVersion.LATEST)
  private val plugins: MutableTomlTable = MutableTomlTable(TomlVersion.LATEST)

  init {
    toml.apply {
      set("versions", versions)
      set("libraries", libraries)
      set("bundles", bundles)
      set("plugins", plugins)
    }
  }

  fun addVersion(alias: String, value: String) {
    versions.set(alias, value)
  }

  fun addLibrary(alias: String, group: String, name: String, version: String, isRef: Boolean) {
    addLibrary(alias, group, name, createVersion(version, isRef))
  }

  fun addLibrary(alias: String, group: String, name: String, version: VersionConstraint) {
    addLibrary(alias, group, name, createVersion(version))
  }

  private fun addLibrary(alias: String, group: String, name: String, version: Any) {
    val lib = MutableTomlTable(TomlVersion.LATEST)
    lib.set("group", group)
    lib.set("name", name)
    lib.set("version", version)
    libraries.set(alias, lib)
  }

  fun addBundle(alias: String, libraries: Iterable<String>) {
    val array = MutableTomlArray(false)
    libraries.forEach { array.append(it, oneOne) }
    bundles.set(alias, array)
  }

  fun addPlugin(alias: String, id: String, version: String, isRef: Boolean) {
    val plugin = MutableTomlTable(TomlVersion.LATEST)
    plugin.set("id", id)
    plugin.set("version", createVersion(version, isRef))
    plugins.set(alias, plugin)
  }

  fun containsLibraryAlias(alias: String) = libraries.contains(alias)

  fun getLibrary(alias: String) =
    requireNotNull(libraries.getTable(alias)) { "No library found for the alias ${alias}" }

  fun getLibraryVersionString(alias: String): String {
    val lib = getLibrary(alias)

    return when (val v = lib.get("version")) {
      is TomlTable -> v.getString("ref")!!
      is String -> v
      else -> throw IllegalArgumentException("Unable to resolve version value ${v}")
    }
  }

  fun getVersionAliases(): Set<String> {
    return versions.dottedKeySet()
  }

  fun toToml(): String {
    return toml.toToml()
  }

  private fun createVersion(version: String, isRef: Boolean): Any {
    return when {
      isRef -> MutableTomlTable(TomlVersion.LATEST).apply { set("ref", version) }
      else -> version
    }
  }

  private fun createVersion(version: VersionConstraint): Any {
    if (!isRichVersion(version)) {
      return version.requiredVersion
    }
    return MutableTomlTable(TomlVersion.LATEST).apply {
      version.requiredVersion
        .takeIf { it.isNotBlank() && it != version.strictVersion }
        ?.also { set("require", it) }
      version.strictVersion.takeIf { it.isNotBlank() }?.also { set("strictly", it) }
      version.preferredVersion.takeIf { it.isNotBlank() }?.also { set("prefer", it) }
      version.branch?.takeIf { it.isNotBlank() }?.also { set("branch", it) }
      version.rejectedVersions
        .takeIf { it.isNotEmpty() }
        ?.also {
          val arr = MutableTomlArray(false).apply { it.forEach { v -> append(v, oneOne) } }
          set("reject", arr)
        }
    }
  }

  private fun isRichVersion(version: VersionConstraint): Boolean {
    return with(version) {
      strictVersion.isNotBlank() ||
        preferredVersion.isNotBlank() ||
        !branch.isNullOrBlank() ||
        rejectedVersions.isNotEmpty()
    }
  }

  private fun MutableTomlTable.set(key: String, value: Any) = set(key, value, oneOne)

  override fun iterator(): Iterator<GeneratedLibrary> {
    return GeneratedLibraryIterator(libraries, versions)
  }

  // false positive since we delegate to a different iterator
  @Suppress("detekt:IteratorNotThrowingNoSuchElementException")
  private class GeneratedLibraryIterator(
    private val libraries: TomlTable,
    private val versions: TomlTable,
  ) : Iterator<GeneratedLibrary> {

    private val keyIterator: Iterator<String> by lazy {
      libraries
        .dottedKeySet(true)
        .asSequence()
        .filter { !it.endsWith(".version") && libraries.isTable(it) }
        .filter { libraries.getTable(it)?.contains("version") == true }
        .iterator()
    }

    override fun hasNext(): Boolean = keyIterator.hasNext()

    override fun next(): GeneratedLibrary {
      val key = keyIterator.next()
      val lib = libraries.getTable(key)!!
      val (version, isRef) =
        when (val v = lib.get("version")) {
          is String -> v to false
          is TomlTable ->
            when {
              v.contains("strictly") -> v.getString("strictly")!! to false
              v.contains("require") -> v.getString("require")!! to false
              v.contains("prefer") -> v.getString("prefer")!! to false
              else -> v.getString("ref")!! to true
            }
          else -> throw IllegalArgumentException("Unable to resolve version value ${v}")
        }

      val (actualVersion, refName) =
        when {
          isRef -> versions.getString(version)!! to version
          else -> version to null
        }

      return GeneratedLibrary(
        alias = key,
        group = lib.getString("group")!!,
        name = lib.getString("name")!!,
        version = actualVersion,
        versionRef = refName,
      )
    }
  }
}
