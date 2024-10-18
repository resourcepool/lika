package li.lika.client.http.security

import org.springframework.web.reactive.function.client.ClientRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class AccessTokenAuthenticator(private val credentials: AccessTokenCredentials): Authenticator {

    override fun getBearerToken(): String {
        return credentials.accessToken
    }

    override fun reauthenticate(): Mono<String> {
        return Mono.error(UnsupportedOperationException())
    }

    override fun <T> wrap(mono: Mono<T>): Mono<T> {
        return mono
    }

    override fun <T> wrap(flux: Flux<T>): Flux<T> {
        return flux
    }

    override fun apply(req: ClientRequest): Mono<ClientRequest> {
        return Mono.just(ClientRequest.from(req)
                .header("Authorization", "Bearer ${getBearerToken()}").build())
    }
}
