package dev.aga.gradle.plugin.versioncatalogs.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

internal class POMFetcherChainTest {
    @Test
    fun testFetch() {
        val chain =
            POMFetcherChain(
                LocalPOMFetcher("/not/a/real/directory"),
                LocalPOMFetcher("src/test/resources/poms"),
            )
        val model = chain.fetch("org.assertj", "assertj-bom", "3.24.2")
        assertThat(model.artifactId).isEqualTo("assertj-bom")
    }

    @Test
    fun testFetch_NotFound() {
        val chain =
            POMFetcherChain(
                LocalPOMFetcher("/not/a/real/directory"),
                LocalPOMFetcher("/also/not/a/real/directory"),
            )
        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
            chain.fetch("org.assertj", "assertj-bom", "3.24.2")
        }
    }
}
