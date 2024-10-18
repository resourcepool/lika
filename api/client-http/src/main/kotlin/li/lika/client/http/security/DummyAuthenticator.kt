package li.lika.client.http.security

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class DummyAuthenticator(): Authenticator {

    override fun getBearerToken(): String? {
        return null
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

}
