package li.lika.client.http.domain

import li.lika.common.models.Domain
import li.lika.common.models.DomainCreator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 *
 */
interface DomainClient {
    fun getAll(admin: Boolean = false): Flux<Domain>
    fun getOne(id: String): Mono<Domain>
    fun create(req: DomainCreator): Mono<Domain>
    fun deleteOne(id: String): Mono<Void>
}
