package li.lika.client.http.account

import li.lika.client.http.exceptions.FrogLinkClientErrorHandler
import li.lika.client.http.security.Authenticator
import li.lika.client.http.security.DummyAuthenticator
import li.lika.common.models.Account
import li.lika.common.models.AccountCreator
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class AccountClientImpl(baseUrl: String, authenticator: Authenticator = DummyAuthenticator(), val errorHandler: FrogLinkClientErrorHandler) : AccountClient {

    private val logger = LoggerFactory.getLogger(AccountClientImpl::class.java)
    private val authenticator: Authenticator
    private val webClient: WebClient

    init {
        this.authenticator = authenticator
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .filter(ExchangeFilterFunction.ofRequestProcessor(this.authenticator))
                .build()
    }

    override fun getAll(): Flux<Account> {
        return authenticator.wrap(webClient.get()
            .uri("/api/admin/accounts")
            .retrieve()
            .bodyToFlux<Account>()
            .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
        )
    }

    override fun getOne(id: String): Mono<Account> {
         return authenticator.wrap(webClient.get()
            .uri("/api/admin/accounts/${id}")
            .retrieve()
            .bodyToMono<Account>()
             .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
         )
    }

    override fun create(req: AccountCreator): Mono<Account> {
        return authenticator.wrap(webClient.post()
            .uri("/api/admin/accounts")
            .bodyValue(req)
            .retrieve()
            .bodyToMono<Account>()
            .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
        )
    }

    override fun deleteOne(id: String): Mono<Void> {
        return authenticator.wrap(webClient.delete()
            .uri("/api/admin/accounts/${id}")
            .retrieve()
            .bodyToMono<Void>()
            .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
        )
    }

}
