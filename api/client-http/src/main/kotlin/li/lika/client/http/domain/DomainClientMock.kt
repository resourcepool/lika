package li.lika.client.http.domain

import li.lika.common.models.Account
import li.lika.common.models.AccountCreator
import li.lika.common.models.Domain
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class DomainClientMock: DomainClient {

    val logger = KotlinLogging.logger {  }

    override fun getAll(admin: Boolean): Flux<Domain> {
        logger.info("[mock-lika-domain-account] no-Op getAll()")
        return Flux.empty()
    }

    override fun getOne(id: String): Mono<Domain> {
        logger.info("[mock-lika-domain-account] no-Op getOne()")
        return Mono.empty()
    }

    override fun create(req: AccountCreator): Mono<Account> {
        logger.info("[mock-lika-domain-account] no-Op create()")
        return Mono.empty()
    }

    override fun deleteOne(id: String): Mono<Void> {
        logger.info("[mock-lika-domain-account] no-Op deleteOne()")
        return Mono.empty()
    }
}
