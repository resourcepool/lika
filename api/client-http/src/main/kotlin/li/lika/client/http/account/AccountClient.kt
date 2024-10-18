package li.lika.client.http.account

import li.lika.common.models.Account
import li.lika.common.models.AccountCreator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 *
 */
interface AccountClient {
    fun getAll(): Flux<Account>
    fun create(req: AccountCreator): Mono<Account>
    fun deleteOne(id: String): Mono<Void>
    fun getOne(id: String): Mono<Account>
}
