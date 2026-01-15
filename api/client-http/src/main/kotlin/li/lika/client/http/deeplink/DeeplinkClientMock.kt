package li.lika.client.http.deeplink

import li.lika.common.models.Deeplink
import li.lika.common.models.DeeplinkCreator
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class DeeplinkClientMock: DeeplinkClient {

    val logger = KotlinLogging.logger {  }

    override fun getSlugSuggestions(domainId: String, slug: String): Flux<String> {
        logger.info("[mock-lika-deeplink-account] no-Op getSlugSuggestions()")
        return Flux.empty()
    }

    override fun findOneByDomainIdAndSlug(domainId: String, slug: String): Mono<Deeplink> {
        logger.info("[mock-lika-deeplink-account] no-Op findOneByDomainIdAndSlug()")
        return Mono.empty()
    }

    override fun create(pl: DeeplinkCreator): Mono<Deeplink> {
        logger.info("[mock-lika-deeplink-account] no-Op create()")
        return Mono.empty()
    }

    override fun edit(pl: DeeplinkCreator): Mono<Deeplink> {
        logger.info("[mock-lika-deeplink-account] no-Op edit()")
        return Mono.empty()
    }

    override fun getAll(domainId: String): Flux<Deeplink> {
        logger.info("[mock-lika-deeplink-account] no-Op getAll()")
        return Flux.empty()
    }

    override fun deleteBySlug(domainId: String, slug: String): Mono<Any> {
        logger.info("[mock-lika-deeplink-account] no-Op deleteBySlug()")
        return Mono.empty()
    }

    override fun deleteOne(id: String): Mono<Any> {
        logger.info("[mock-lika-deeplink-account] no-Op deleteOne()")
        return Mono.empty()
    }

    override fun assertSlugAvailable(domainId: String, slug: String): Mono<Any> {
        logger.info("[mock-lika-deeplink-account] no-Op assertSlugAvailable()")
        return Mono.empty()
    }
}
