package li.lika.security

import com.github.benmanes.caffeine.cache.Caffeine
import li.lika.common.models.Account
import li.lika.common.security.AccountRegistry
import li.lika.security.exceptions.WrappedAuthenticationException
import li.lika.security.models.AccessTokenAuthentication
import li.lika.security.models.DummyAuthentication
import li.lika.security.props.SecurityProperties
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.Authentication
import java.util.concurrent.TimeUnit

class AuthManager(private val registry: AccountRegistry, private val props: SecurityProperties) : AuthenticationManager {
    private val logger = LoggerFactory.getLogger(AuthManager::class.java)
    private val keyCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build<String, Account>()

    override fun authenticate(authentication: Authentication): Authentication {
        logger.debug("Will attempt to authenticate token " + authentication.credentials)
        val auth = authentication as DummyAuthentication
        val token = auth.credentials
        if (token == null) {
            logger.debug("No Credentials provided: Anonymous")
            return DummyAuthentication.anonymous()
        }
        return authenticateSecret(auth)
    }

    private fun authenticateSecret(auth: DummyAuthentication): Authentication {
        val accessToken = auth.credentials!!
        val organization = keyCache.get(accessToken, registry::findByAccessToken)
        if (organization == null) {
            logger.warn("Access Token is invalid!")
            throw WrappedAuthenticationException()
        }
        logger.debug("Access Token is valid!")
        if (organization.defaultAccessToken == accessToken) {
            return AccessTokenAuthentication.ofReadWriteAccessToken(organization, accessToken)
        }
        return AccessTokenAuthentication.ofReadAccessToken(organization, accessToken)
    }

    init {

    }
}
