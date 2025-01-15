package dev.aga.gradle.versioncatalogs.assertion

import java.nio.file.Path
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions
import org.assertj.core.error.ShouldHaveSize
import org.tomlj.Toml
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
        is TomlArray -> TomlArrayAssert.assertThat(value).isEqualTo(expected.getArray(key)!!)
        is TomlTable -> assertThat(value).isEqualTo(expected.getTable(key)!!)
        else -> Assertions.assertThat(value).isEqualTo(expected.get(key))
      }
    }

    return this
  }

  fun isEqualTo(expected: Path): TomlTableAssert {
    val expected = Toml.parse(expected)
    return isEqualTo(expected)
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

    fun assertThat(path: Path): TomlTableAssert {
      Assertions.assertThat(path).exists()
      return with(Toml.parse(path)) {
        Assertions.assertThat(hasErrors())
          .withFailMessage {
            "Expected TOML to not have any parse errors but found ${errors().size}"
          }
          .isFalse

        assertThat(this)
      }
    }
  }
}
