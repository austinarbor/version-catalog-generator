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
        isRef: Boolean = false,
    ) {
        val lib = MutableTomlTable(TomlVersion.LATEST)
        lib.set("group", group, oneOne)
        lib.set("name", name, oneOne)
        if (isRef) {
            val v = MutableTomlTable(TomlVersion.LATEST)
            v.set("ref", version, oneOne)
            lib.set("version", v, oneOne)
        } else {
            lib.set("version", version, oneOne)
        }
        libraries.set(alias, lib, oneOne)
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
