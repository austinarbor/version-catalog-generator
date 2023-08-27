package dev.aga.gradle.plugin.versioncatalogs.service

import org.apache.maven.model.Dependency
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

internal class GradleCachePOMFetcherTest {
    @Test
    fun testFetch() {
        val dep =
            Dependency().apply {
                groupId = "org.apache.maven"
                artifactId = "maven-model"
                version = "3.9.4"
            }
        val fetcher = GradleCachePOMFetcher()
        val model = fetcher.fetch(dep)
        assertThat(model.artifactId).isEqualTo("maven-model")
    }

    @Test
    fun testFetch_NotFound() {
        val dep =
            Dependency().apply {
                groupId = "dev.aga"
                artifactId = "not-real"
                version = "1.0.0"
            }
        val fetcher = GradleCachePOMFetcher()
        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy { fetcher.fetch(dep) }
    }
}
