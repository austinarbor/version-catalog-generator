package dev.aga.gradle.plugin.versioncatalogs.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

internal class LocalPOMFetcherTest {
    @Test
    fun testFetch() {
        val fetcher = LocalPOMFetcher("src/test/resources/poms")
        val model = fetcher.fetch("org.assertj", "assertj-bom", "3.24.2")
        assertThat(model.artifactId).isEqualTo("assertj-bom")
    }

    @Test
    fun testFetch_DoesNotExist() {
        val fetcher = LocalPOMFetcher("src/test/resources/poms")
        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
            fetcher.fetch("dev.aga", "fake-artifact", "1.0.0")
        }
    }
}
