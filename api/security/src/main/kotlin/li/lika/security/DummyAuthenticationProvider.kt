package li.lika.security

import li.lika.security.models.DummyAuthentication
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.core.Authentication

class DummyAuthenticationProvider: AuthenticationProvider {

    override fun authenticate(authentication: Authentication): Authentication {
        return authentication
    }

    override fun supports(authentication: Class<*>): Boolean {
        return DummyAuthentication::class.java.isAssignableFrom(authentication)
    }
}
