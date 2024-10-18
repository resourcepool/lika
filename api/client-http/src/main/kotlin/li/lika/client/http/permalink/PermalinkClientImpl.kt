package li.lika.client.http.permalink

import li.lika.client.http.exceptions.FrogLinkClientErrorHandler
import li.lika.client.http.security.Authenticator
import li.lika.client.http.security.DummyAuthenticator
import li.lika.common.models.Permalink
import li.lika.common.models.PermalinkCreator
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

class PermalinkClientImpl(
    baseUrl: String,
    authenticator: Authenticator = DummyAuthenticator(),
    val errorHandler: FrogLinkClientErrorHandler
) : PermalinkClient {

    private val logger = LoggerFactory.getLogger(PermalinkClientImpl::class.java)
    private val authenticator: Authenticator
    private val webClient: WebClient

    init {
        this.authenticator = authenticator
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .filter(ExchangeFilterFunction.ofRequestProcessor(this.authenticator))
            .build()
    }

    override fun getSlugSuggestions(domainId: String, slug: String): Flux<String> {
        return authenticator.wrap(
            webClient.get()
                .uri(
                    "/api/search/slug/suggestions/${slug}?domainId=${
                        URLEncoder.encode(
                            domainId.trim().lowercase(),
                            UTF_8
                        )
                    }"
                )
                .retrieve()
                .bodyToMono<List<String>>()
                .flatMapMany { Flux.fromIterable(it) }
                .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
        )
    }

    override fun findOneByDomainIdAndSlug(domainId: String, slug: String): Mono<Permalink> {
        return authenticator.wrap(
            webClient.get()
                .uri("/api/search/slug/${slug}?domainId=${URLEncoder.encode(domainId.trim().toLowerCase(), UTF_8)}")
                .retrieve()
                .bodyToMono<Permalink>()
                .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
        )
    }

    override fun create(pl: PermalinkCreator): Mono<Permalink> {
        return authenticator.wrap(
            webClient.post()
                .uri("/api/links")
                .bodyValue(pl)
                .retrieve()
                .bodyToMono<Permalink>()
                .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
        )
    }

    override fun edit(pl: PermalinkCreator): Mono<Permalink> {
        return authenticator.wrap(
            webClient.put()
                .uri("/api/links")
                .bodyValue(pl)
                .retrieve()
                .bodyToMono<Permalink>()
                .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
        )
    }

    override fun getAll(domainId: String): Flux<Permalink> {
        return authenticator.wrap(
            webClient.get()
                .uri("/api/links?domainId=${URLEncoder.encode(domainId.trim().lowercase(), UTF_8)}")
                .retrieve()
                .bodyToFlux<Permalink>()
                .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
        )
    }

    override fun assertSlugAvailable(domainId: String, slug: String): Mono<Any> {
        return authenticator.wrap(
            webClient.get()
                .uri("/api/links/slug/${slug}?domainId=${URLEncoder.encode(domainId.trim().lowercase(), UTF_8)}")
                .retrieve()
                .bodyToMono<Any>()
                .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
        )
    }

    override fun deleteBySlug(domainId: String, slug: String): Mono<Any> {
        return authenticator.wrap(
            webClient.delete()
                .uri("/api/links/slug/${slug}?domainId=${URLEncoder.encode(domainId.trim().lowercase(), UTF_8)}")
                .retrieve()
                .bodyToMono<Any>()
                .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
        )
    }

    override fun deleteOne(id: String): Mono<Any> {
        return authenticator.wrap(
            webClient.delete()
                .uri("/api/links/${id}")
                .retrieve()
                .bodyToMono<Any>())
                .onErrorMap(WebClientResponseException::class.java, errorHandler::parseWebClientResponseException)
    }
}
