package li.lika.client.http.permalink

import li.lika.common.models.Permalink
import li.lika.common.models.PermalinkCreator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 *
 */
interface PermalinkClient {
    fun getSlugSuggestions(domainId: String, slug: String): Flux<String>
    fun findOneByDomainIdAndSlug(domainId: String, slug: String): Mono<Permalink>
    fun create(pl: PermalinkCreator): Mono<Permalink>
    fun edit(pl: PermalinkCreator): Mono<Permalink>
    fun getAll(domainId: String): Flux<Permalink>
    fun deleteBySlug(domainId: String, slug: String): Mono<Any>
    fun deleteOne(id: String): Mono<Any>
    fun assertSlugAvailable(domainId: String, slug: String): Mono<Any>
}
