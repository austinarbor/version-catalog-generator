package org.tomlj

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class TomlContainerTest {
  @Test
  fun `getLibrary throws IllegalArgumentException when alias is missing`() {
    val container = TomlContainer()
    assertThatIllegalArgumentException().isThrownBy { container.getLibrary("anything") }
  }

  @Test
  fun testGetLibraryVersionString() {
    val container = TomlContainer()
    container.addLibrary(
      "my-plugin1",
      "dev.aga.gradle",
      "version-catalog-generator",
      "1.4.1",
      false,
    )
    assertThat(container.getLibraryVersionString("my-plugin1")).isEqualTo("1.4.1")

    container.addLibrary(
      "my-plugin2",
      "dev.aga.gradle",
      "version-catalog-generator",
      "plugin",
      true,
    )
    assertThat(container.getLibraryVersionString("my-plugin2")).isEqualTo("plugin")
  }
}
