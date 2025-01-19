package dev.aga.gradle.versioncatalogs.assertion

import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions
import org.assertj.core.error.ShouldHaveSize
import org.tomlj.TomlArray

class TomlArrayAssert(actual: TomlArray) :
  AbstractAssert<TomlArrayAssert, TomlArray>(actual, TomlArrayAssert::class.java) {

  fun hasSize(size: Int): TomlArrayAssert {
    isNotNull()
    if (actual.size() != size) {
      failWithMessage(ShouldHaveSize.shouldHaveSize(actual, actual.size(), size).create())
    }
    return this
  }

  fun isEqualTo(expected: TomlArray): TomlArrayAssert {
    isNotNull()
    hasSize(expected.size())
    for (i in 0 until actual.size()) {
      Assertions.assertThat(actual[i]).isEqualTo(expected[i])
    }
    return this
  }

  fun containsExactlyInAnyOrderElementsOf(expected: TomlArray): TomlArrayAssert {
    isNotNull()
    hasSize(expected.size())
    Assertions.assertThat(actual.toList()).containsExactlyInAnyOrderElementsOf(expected.toList())
    return this
  }

  companion object {
    fun assertThat(actual: TomlArray): TomlArrayAssert = TomlArrayAssert(actual)
  }
}
