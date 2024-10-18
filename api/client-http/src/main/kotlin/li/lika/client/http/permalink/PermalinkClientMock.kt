package li.lika.client.http.permalink

import li.lika.common.models.Permalink
import li.lika.common.models.PermalinkCreator
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class PermalinkClientMock: PermalinkClient {

    val logger = KotlinLogging.logger {  }

    override fun getSlugSuggestions(domainId: String, slug: String): Flux<String> {
        logger.info("[mock-lika-permalink-account] no-Op getSlugSuggestions()")
        return Flux.empty()
    }

    override fun findOneByDomainIdAndSlug(domainId: String, slug: String): Mono<Permalink> {
        logger.info("[mock-lika-permalink-account] no-Op findOneByDomainIdAndSlug()")
        return Mono.empty()
    }

    override fun create(pl: PermalinkCreator): Mono<Permalink> {
        logger.info("[mock-lika-permalink-account] no-Op create()")
        return Mono.empty()
    }

    override fun edit(pl: PermalinkCreator): Mono<Permalink> {
        logger.info("[mock-lika-permalink-account] no-Op edit()")
        return Mono.empty()
    }

    override fun getAll(domainId: String): Flux<Permalink> {
        logger.info("[mock-lika-permalink-account] no-Op getAll()")
        return Flux.empty()
    }

    override fun deleteBySlug(domainId: String, slug: String): Mono<Any> {
        logger.info("[mock-lika-permalink-account] no-Op deleteBySlug()")
        return Mono.empty()
    }

    override fun deleteOne(id: String): Mono<Any> {
        logger.info("[mock-lika-permalink-account] no-Op deleteOne()")
        return Mono.empty()
    }

    override fun assertSlugAvailable(domainId: String, slug: String): Mono<Any> {
        logger.info("[mock-lika-permalink-account] no-Op assertSlugAvailable()")
        return Mono.empty()
    }
}
