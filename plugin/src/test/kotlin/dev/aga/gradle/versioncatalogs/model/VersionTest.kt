package dev.aga.gradle.versioncatalogs.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VersionTest {
  @Test
  fun testIsRef() {
    val v1 = Version("\${a.version}", "a.version", "1.0.0")
    assertThat(v1.isRef).isTrue

    val v2 = Version("1.0.0", "1.0.0", "1.0.0")
    assertThat(v2.isRef).isFalse
  }
}
