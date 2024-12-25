package dev.aga.gradle.versioncatalogs.assertion

import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions
import org.assertj.core.error.ShouldHaveSize
import org.tomlj.TomlArray
import org.tomlj.TomlTable

class TomlTableAssert(actual: TomlTable) :
    AbstractAssert<TomlTableAssert, TomlTable>(actual, TomlTableAssert::class.java) {

    fun isEqualTo(expected: TomlTable): TomlTableAssert {
        isNotNull()
        hasSize(expected.size())
        hasDottedKeySet(expected.dottedKeySet())
        actual.dottedKeySet().forEach { key ->
            when (val value = actual.get(key)) {
                is TomlArray ->
                    TomlArrayAssert.assertThat(value).isEqualTo(expected.getArray(key)!!)
                is TomlTable -> assertThat(value).isEqualTo(expected.getTable(key)!!)
                else -> Assertions.assertThat(value).isEqualTo(expected.get(key))
            }
        }

        return this
    }

    fun hasSize(size: Int): TomlTableAssert {
        isNotNull()
        if (actual.size() != size) {
            failWithMessage(ShouldHaveSize.shouldHaveSize(actual, actual.size(), size).create())
        }
        return this
    }

    fun hasDottedKeySet(keys: Set<String>): TomlTableAssert {
        isNotNull()
        Assertions.assertThat(actual.dottedKeySet()).containsExactlyInAnyOrderElementsOf(keys)
        return this
    }

    companion object {
        fun assertThat(actual: TomlTable): TomlTableAssert = TomlTableAssert(actual)
    }
}
