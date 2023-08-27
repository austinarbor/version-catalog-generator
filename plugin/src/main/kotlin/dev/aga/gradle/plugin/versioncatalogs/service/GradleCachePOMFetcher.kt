package dev.aga.gradle.plugin.versioncatalogs.service

import java.io.File
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.isDirectory
import kotlin.io.path.walk
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.slf4j.LoggerFactory

internal class GradleCachePOMFetcher : POMFetcher {

    @OptIn(ExperimentalPathApi::class)
    override fun fetch(dep: Dependency): Model {
        val root = getGradleDir().toPath()
        val cacheDir =
            root.resolve(
                Paths.get(
                    "caches",
                    "modules-2",
                    "files-2.1",
                    dep.groupId,
                    dep.artifactId,
                    dep.version,
                ),
            )
        cacheDir.walk(PathWalkOption.INCLUDE_DIRECTORIES).forEach {
            if (it.isDirectory()) {
                val fetcher = LocalPOMFetcher(it.toString())
                try {
                    return fetcher.fetch(dep)
                } catch (e: RuntimeException) {
                    logger.debug(
                        "Unable to resolve POM from directory ${it.toString()}",
                    )
                }
            }
        }
        throw RuntimeException("Unable to find POM in Gradle cache")
    }

    private fun getGradleDir(): File {
        try {
            lock.lock()
            return settingsHomeDirectory
        } finally {
            lock.unlock()
        }
    }

    companion object {
        val logger = LoggerFactory.getLogger(GradleCachePOMFetcher::class.java)
        val lock = ReentrantLock()

        var settingsHomeDirectory: File =
            Paths.get(System.getProperty("user.home"), ".gradle").toFile()

        fun setSettingsDirectory(dir: File) {
            try {
                lock.lock()
                settingsHomeDirectory = dir
            } finally {
                lock.unlock()
            }
        }
    }
}
