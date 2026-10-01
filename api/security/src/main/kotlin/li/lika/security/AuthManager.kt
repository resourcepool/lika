package li.lika.security

import li.lika.common.models.Account
import li.lika.common.security.AccountRegistry
import li.lika.security.exceptions.WrappedAuthenticationException
import li.lika.security.models.AccessTokenAuthentication
import li.lika.security.models.DummyAuthentication
import li.lika.security.props.SecurityProperties
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.Authentication

class AuthManager(private val registry: AccountRegistry, private val props: SecurityProperties) : AuthenticationManager {
    private val logger = LoggerFactory.getLogger(AuthManager::class.java)

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
        // Lookups are cached by the registry, which evicts on every account change
        val organization = registry.findByAccessToken(accessToken)
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
}
