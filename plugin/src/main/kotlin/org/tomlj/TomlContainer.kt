package org.tomlj

class TomlContainer {
    private val zeroZero = TomlPosition.positionAt(1, 1)
    private val toml: MutableTomlTable = MutableTomlTable(TomlVersion.LATEST)
    private val versions: MutableTomlTable = MutableTomlTable(TomlVersion.LATEST)
    private val libraries: MutableTomlTable = MutableTomlTable(TomlVersion.LATEST)
    private val bundles: MutableTomlTable = MutableTomlTable(TomlVersion.LATEST)

    init {
        toml.set("versions", versions, zeroZero)
        toml.set("libraries", libraries, zeroZero)
        toml.set("bundles", bundles, zeroZero)
    }

    fun addVersion(alias: String, value: String) {
        versions.set(alias, value, zeroZero)
    }

    fun addLibrary(
        alias: String,
        group: String,
        name: String,
        version: String,
        isRef: Boolean = false,
    ) {
        val lib = MutableTomlTable(TomlVersion.LATEST)
        lib.set("group", group, zeroZero)
        lib.set("name", name, zeroZero)
        if (isRef) {
            val v = MutableTomlTable(TomlVersion.LATEST)
            v.set("ref", version, zeroZero)
            lib.set("version", v, zeroZero)
        } else {
            lib.set("version", version, zeroZero)
        }
        libraries.set(alias, lib, zeroZero)
    }

    fun addBundle(alias: String, libraries: Iterable<String>) {
        val array = MutableTomlArray(false)
        libraries.forEach { array.append(it, zeroZero) }
        bundles.set(alias, array, zeroZero)
    }

    fun toToml(): String {
        return toml.toToml()
    }
}
