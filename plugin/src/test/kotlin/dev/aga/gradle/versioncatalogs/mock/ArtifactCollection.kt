package dev.aga.gradle.versioncatalogs.mock

import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider

internal class ArtifactCollection(val result: ResolvedArtifactResult) : ArtifactCollection {
    override fun iterator(): MutableIterator<ResolvedArtifactResult> {
        return mutableListOf(result).iterator()
    }

    override fun getArtifactFiles(): FileCollection {
        TODO("Not yet implemented")
    }

    override fun getArtifacts(): MutableSet<ResolvedArtifactResult> {
        return mutableSetOf(result)
    }

    override fun getResolvedArtifacts(): Provider<MutableSet<ResolvedArtifactResult>> {
        TODO("Not yet implemented")
    }

    override fun getFailures(): MutableCollection<Throwable> {
        TODO("Not yet implemented")
    }
}
