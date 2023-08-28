package dev.aga.gradle.versioncatalogs.service

import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.slf4j.LoggerFactory

class POMFetcherChain(private val fetchers: List<POMFetcher>) : POMFetcher {

    constructor(vararg fetchers: POMFetcher) : this(fetchers.toList())

    val logger = LoggerFactory.getLogger(POMFetcherChain::class.java)

    override fun fetch(dep: Dependency): Model {
        for (fetcher in fetchers) {
            try {
                return fetcher.fetch(dep)
            } catch (e: Exception) {
                logger.debug("Unable to fetch POM", e)
            }
        }
        throw RuntimeException("Unable to fetch POM from any of the fetchers in the chain")
    }
}
