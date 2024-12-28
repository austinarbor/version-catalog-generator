package dev.aga.gradle.versioncatalogs

import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExtensionsTest {
    @Test
    fun `versionRef creates a catalog parser for the file`() {
        val file = Paths.get("src", "test", "resources", "tomls", "libs.versions.toml").toFile()
        val ref = file.versionRef("groovy")
        assertThat(ref.getValue()).isEqualTo("3.0.5")
    }
}
