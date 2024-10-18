package li.lika.client.http.account

import li.lika.common.models.Account
import li.lika.common.models.AccountCreator
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class AccountClientMock: AccountClient {

    val logger = KotlinLogging.logger {  }

    override fun getAll(): Flux<Account> {
        logger.info("[mock-lika-client-account] no-Op getAll()")
        return Flux.empty()
    }

    override fun create(req: AccountCreator): Mono<Account> {
        logger.info("[mock-lika-client-account] no-Op create()")
        return Mono.empty()
    }

    override fun deleteOne(id: String): Mono<Void> {
        logger.info("[mock-lika-client-account] no-Op create()")
        return Mono.empty()
    }

    override fun getOne(id: String): Mono<Account> {
        logger.info("[mock-lika-client-account] no-Op create()")
        return Mono.empty()
    }
}
