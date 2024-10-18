package li.lika.client.http.domain

import li.lika.client.http.account.AccountClientImpl
import li.lika.client.http.exceptions.FrogLinkClientErrorHandler
import li.lika.client.http.security.Authenticator
import li.lika.client.http.security.DummyAuthenticator
import li.lika.common.models.Account
import li.lika.common.models.AccountCreator
import li.lika.common.models.Domain
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class DomainClientImpl(
    baseUrl: String,
    authenticator: Authenticator = DummyAuthenticator(),
    val errorHandler: FrogLinkClientErrorHandler
) : DomainClient {

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

    override fun getAll(admin: Boolean): Flux<Domain> {
        if (admin) {
            return authenticator.wrap(webClient.get()
                    .uri("/api/admin/domains")
                    .retrieve()
                    .bodyToFlux<Domain>()
                    .doOnEach { logger.info(it.toString()) }
                    .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException))
        }
        return authenticator.wrap(
            webClient.get()
                .uri("/api/domains")
                .retrieve()
                .bodyToFlux<Domain>()
                .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
        )
    }

    override fun getOne(id: String): Mono<Domain> {
        return authenticator.wrap(
            webClient.get()
                .uri("/api/admin/domains/${id}")
                .retrieve()
                .bodyToMono<Domain>()
                .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
        )
    }

    override fun create(req: AccountCreator): Mono<Account> {
        return authenticator.wrap(
            webClient.post()
                .uri("/api/admin/domains")
                .bodyValue(req)
                .retrieve()
                .bodyToMono()
        )
    }

    override fun deleteOne(id: String): Mono<Void> {
        return authenticator.wrap(
            webClient.delete()
                .uri("/api/admin/domains/${id}")
                .retrieve()
                .bodyToMono<Void>()
                .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
        )
    }

}
