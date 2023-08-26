package dev.aga.gradle.plugin.versioncatalogs.service

import org.apache.maven.model.Dependency
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.junit.jupiter.api.Test

internal class RemotePOMFetcherTest {

    private val fetcher = RemotePOMFetcher("https://repo1.maven.org/maven2")

    @Test
    fun testFetch_NotPOM() {
        val dep =
            Dependency().apply {
                groupId = "dev.aga"
                artifactId = "sfm-jooq-kotlin"
                version = "0.0.2"
            }
        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy { fetcher.fetch(dep) }
    }

    @Test
    fun testFetch() {
        val dep =
            Dependency().apply {
                groupId = "org.springframework.boot"
                artifactId = "spring-boot-dependencies"
                version = "3.1.2"
            }
        val result = fetcher.fetch(dep)
        assertThat(result.groupId).isEqualTo("org.springframework.boot")
        assertThat(result.artifactId).isEqualTo("spring-boot-dependencies")
        assertThat(result.version).isEqualTo("3.1.2")
        assertThat(result.dependencyManagement.dependencies).hasSize(398)
    }

    @Test
    fun testFetch_GradleDependency() {
        val dep =
            DefaultExternalModuleDependency(
                "org.springframework.boot",
                "spring-boot-dependencies",
                "3.1.2",
            )
        val result = fetcher.fetch(dep)
        assertThat(result.groupId).isEqualTo("org.springframework.boot")
        assertThat(result.artifactId).isEqualTo("spring-boot-dependencies")
        assertThat(result.version).isEqualTo("3.1.2")
        assertThat(result.dependencyManagement.dependencies).hasSize(398)
    }
}
