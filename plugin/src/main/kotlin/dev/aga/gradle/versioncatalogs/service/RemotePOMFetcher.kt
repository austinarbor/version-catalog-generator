package dev.aga.gradle.versioncatalogs.service

import java.io.StringReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader

internal open class RemotePOMFetcher(
    private val baseUrl: String,
    private val client: HttpClient = HttpClient.newHttpClient(),
) : POMFetcher {

    override fun fetch(dep: Dependency): Model {
        val request = buildRequest(dep)
        val model = handleResponse(request, client.send(request, BodyHandlers.ofString()))
        return model.apply {
            if (model.version == null) {
                model.version = dep.version
            }
        }
    }

    private fun handleResponse(req: HttpRequest, res: HttpResponse<String>): Model {
        when (res.statusCode()) {
            200 -> return parseBody(res.body())
            else ->
                throw RuntimeException(
                    "Received response code ${res.statusCode()} from ${req.uri()}",
                )
        }
    }

    private fun parseBody(body: String): Model {
        val mavenReader = MavenXpp3Reader()
        val stringReader = StringReader(body)
        return mavenReader.read(stringReader).takeIf { it.packaging == "pom" }
            ?: throw RuntimeException("Invalid pom file")
    }

    private fun buildRequest(dep: Dependency): HttpRequest {
        val uri = buildURI(dep)
        return HttpRequest.newBuilder(uri).GET().build()
    }

    private fun buildURI(dep: Dependency): URI {
        val groupIdPath = splitGroupId(dep.groupId)
        return URI(
            "${baseUrl}/${groupIdPath}/${dep.artifactId}/${dep.version}/${dep.artifactId}-${dep.version}.pom",
        )
    }

    private fun splitGroupId(groupId: String): String {
        return groupId.split(".").joinToString("/")
    }
}
