package org.tomlj

class TomlContainer {
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

    fun addLibrary(
        alias: String,
        group: String,
        name: String,
        version: String,
        isRef: Boolean,
    ) {
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
}
