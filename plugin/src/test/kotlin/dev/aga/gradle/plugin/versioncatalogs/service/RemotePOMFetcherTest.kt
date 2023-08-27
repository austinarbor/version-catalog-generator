package dev.aga.gradle.plugin.versioncatalogs.service

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandler
import org.apache.maven.model.Dependency
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal class RemotePOMFetcherTest {
    private val baseUrl = "https://repo1.maven.org/maven2"

    private val fetcher = RemotePOMFetcher(baseUrl)

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

    @Test
    fun testFetch_Non200Responses() {
        val mockResponse = mock<HttpResponse<String>> { on { statusCode() } doReturn 400 }
        val client =
            mock<HttpClient> {
                on { send(any<HttpRequest>(), any<BodyHandler<String>>()) } doReturn mockResponse
            }
        val fetcher = RemotePOMFetcher(baseUrl, client)
        val dep =
            Dependency().apply {
                groupId = "org.springframework.boot"
                artifactId = "spring-boot-dependencies"
                version = "3.1.2"
            }
        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy { fetcher.fetch(dep) }
    }
}
