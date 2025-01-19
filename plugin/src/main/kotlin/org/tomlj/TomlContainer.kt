package org.tomlj

import dev.aga.gradle.versioncatalogs.model.GeneratedLibrary

class TomlContainer : Iterable<GeneratedLibrary> {
  private val oneOne = TomlPosition.positionAt(1, 1)
  private val toml: MutableTomlTable = MutableTomlTable(TomlVersion.LATEST)
  private val versions: MutableTomlTable = MutableTomlTable(TomlVersion.LATEST)
  private val libraries: MutableTomlTable = MutableTomlTable(TomlVersion.LATEST)
  private val bundles: MutableTomlTable = MutableTomlTable(TomlVersion.LATEST)

  init {
    toml.set("versions", versions, oneOne)
    toml.set("libraries", libraries, oneOne)
    toml.set("bundles", bundles, oneOne)
  }

  fun addVersion(alias: String, value: String) {
    versions.set(alias, value, oneOne)
  }

  fun addLibrary(alias: String, group: String, name: String, version: String, isRef: Boolean) {
    val lib = MutableTomlTable(TomlVersion.LATEST)
    lib.set("group", group, oneOne)
    lib.set("name", name, oneOne)
    val v: Any =
      if (isRef) {
        MutableTomlTable(TomlVersion.LATEST).apply { set("ref", version, oneOne) }
      } else {
        version
      }
    lib.set("version", v, oneOne)
    libraries.set(alias, lib, oneOne)
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

  fun addBundle(alias: String, libraries: Iterable<String>) {
    val array = MutableTomlArray(false)
    libraries.forEach { array.append(it, oneOne) }
    bundles.set(alias, array, oneOne)
  }

  fun toToml(): String {
    return toml.toToml()
  }

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
        .iterator()
    }

    override fun hasNext(): Boolean = keyIterator.hasNext()

    override fun next(): GeneratedLibrary {
      val key = keyIterator.next()
      val lib = libraries.getTable(key)!!
      val (version, isRef) =
        when (val v = lib.get("version")) {
          is String -> v to false
          is TomlTable -> v.getString("ref")!! to true
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
