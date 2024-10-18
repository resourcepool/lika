package li.lika.client.http.domain

import li.lika.common.models.Account
import li.lika.common.models.AccountCreator
import li.lika.common.models.Domain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 *
 */
interface DomainClient {
    fun getAll(admin: Boolean = false): Flux<Domain>
    fun getOne(id: String): Mono<Domain>
    fun create(req: AccountCreator): Mono<Account>
    fun deleteOne(id: String): Mono<Void>
}
