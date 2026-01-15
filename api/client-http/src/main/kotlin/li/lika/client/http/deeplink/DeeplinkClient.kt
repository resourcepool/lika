package li.lika.client.http.deeplink

import li.lika.common.models.Deeplink
import li.lika.common.models.DeeplinkCreator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 *
 */
interface DeeplinkClient {
    fun getSlugSuggestions(domainId: String, slug: String): Flux<String>
    fun findOneByDomainIdAndSlug(domainId: String, slug: String): Mono<Deeplink>
    fun create(pl: DeeplinkCreator): Mono<Deeplink>
    fun edit(pl: DeeplinkCreator): Mono<Deeplink>
    fun getAll(domainId: String): Flux<Deeplink>
    fun deleteBySlug(domainId: String, slug: String): Mono<Any>
    fun deleteOne(id: String): Mono<Any>
    fun assertSlugAvailable(domainId: String, slug: String): Mono<Any>
}
