package li.lika.client.http

import com.fasterxml.jackson.databind.ObjectMapper
import li.lika.client.http.account.AccountClient
import li.lika.client.http.account.AccountClientImpl
import li.lika.client.http.account.AccountClientMock
import li.lika.client.http.domain.DomainClient
import li.lika.client.http.domain.DomainClientImpl
import li.lika.client.http.domain.DomainClientMock
import li.lika.client.http.exceptions.FrogLinkClientErrorHandler
import li.lika.client.http.deeplink.DeeplinkClient
import li.lika.client.http.deeplink.DeeplinkClientImpl
import li.lika.client.http.deeplink.DeeplinkClientMock
import li.lika.client.http.security.AccessTokenAuthenticator
import li.lika.client.http.security.AccessTokenCredentials
import li.lika.client.http.security.Authenticator
import li.lika.client.http.security.DummyAuthenticator

class LikaClient {
    private constructor(baseUrl: String, authenticator: Authenticator = DummyAuthenticator(), errorHandler: FrogLinkClientErrorHandler = FrogLinkClientErrorHandler(ObjectMapper())) {
        this.deeplink = DeeplinkClientImpl(baseUrl, authenticator, errorHandler)
        this.account = AccountClientImpl(baseUrl, authenticator, errorHandler)
        this.domain = DomainClientImpl(baseUrl, authenticator, errorHandler)
        this.authenticator = authenticator
    }

    private constructor(deeplinkClient: DeeplinkClient, accountClient: AccountClient, domainClient: DomainClient, authenticator: Authenticator) {
        this.deeplink = deeplinkClient
        this.domain = domainClient
        this.account = accountClient
        this.authenticator = authenticator
    }

    val deeplink: DeeplinkClient
    val account: AccountClient
    val domain: DomainClient
    val authenticator: Authenticator

    companion object {

        fun create(baseUrl: String): LikaClient {
            return LikaClient(baseUrl)
        }

        fun create(baseUrl: String, credentials: AccessTokenCredentials): LikaClient {
            return LikaClient(baseUrl, AccessTokenAuthenticator(credentials))
        }

        fun create(baseUrl: String, credentials: AccessTokenCredentials, errorHandler: FrogLinkClientErrorHandler): LikaClient {
            return LikaClient(baseUrl, AccessTokenAuthenticator(credentials), errorHandler)
        }

        fun create(deeplinkClient: DeeplinkClient, accountClient: AccountClient, domainClient: DomainClient, authenticator: Authenticator): LikaClient {
            return LikaClient(deeplinkClient, accountClient, domainClient, authenticator)
        }

        fun mocked(): LikaClient {
            return create(DeeplinkClientMock(), AccountClientMock(), DomainClientMock(), DummyAuthenticator())
        }
    }
}
