package li.lika.client.http.security

import org.springframework.web.reactive.function.client.ClientRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

interface Authenticator : Function<ClientRequest, Mono<ClientRequest>> {

    fun getBearerToken(): String?
    fun reauthenticate(): Mono<String>
    fun <T> wrap(mono: Mono<T>): Mono<T>
    fun <T> wrap(flux: Flux<T>): Flux<T>

    override fun apply(req: ClientRequest): Mono<ClientRequest> {
        return Mono.just(req)
    }
}
