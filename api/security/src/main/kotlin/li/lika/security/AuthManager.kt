package li.lika.security

import li.lika.common.exceptions.MessageAndErrorCodeException
import li.lika.common.security.CredentialsResolver
import li.lika.security.exceptions.MessageAndErrorCodeSecurityException
import li.lika.security.exceptions.WrappedAuthenticationException
import li.lika.security.models.DummyAuthentication
import li.lika.security.models.LikaAuthentication
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.Authentication

/**
 * Authenticates a user token or an API key secret.
 */
class AuthManager(private val resolver: CredentialsResolver) : AuthenticationManager {
    private val logger = LoggerFactory.getLogger(AuthManager::class.java)

    override fun authenticate(authentication: Authentication): Authentication {
        val credentials = (authentication as DummyAuthentication).credentials ?: return DummyAuthentication.anonymous()
        try {
            return LikaAuthentication(resolver.resolve(credentials), credentials)
        } catch (e: MessageAndErrorCodeException) {
            logger.debug("Authentication refused: {}", e.errorCode)
            throw MessageAndErrorCodeSecurityException(e.errorCode, e.message, e, if (e.statusCode == 403) 403 else 401)
        } catch (e: RuntimeException) {
            logger.warn("Authentication failed", e)
            throw WrappedAuthenticationException(e)
        }
    }
}
