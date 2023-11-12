package dev.aga.gradle.versioncatalogs.mock

import java.io.File
import java.nio.file.Path
import org.apache.maven.model.Dependency
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.component.Artifact

internal class ArtifactResult(val root: Path, val source: Dependency) : ResolvedArtifactResult {
    override fun getId(): ComponentArtifactIdentifier {
        TODO("Not yet implemented")
    }

    override fun getType(): Class<out Artifact> {
        TODO("Not yet implemented")
    }

    override fun getFile(): File {
        val fileName = "${source.artifactId}-${source.version}.pom"
        return root.resolve(fileName).toFile()
    }

    override fun getVariant(): ResolvedVariantResult {
        TODO("Not yet implemented")
    }
}
