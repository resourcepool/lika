package li.lika.security

import li.lika.security.exceptions.NoTokenProvidedException
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.core.Authentication
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import java.util.function.Supplier

/**
 * This method verifies that our authentication principal is authenticated and not anonymous.
 * If not, it means no id-token has been provided, therefore a NoTokenProvidedException will be thrown.
 * This Exception will imply that the ServletSecurityFailuresHandler will handle the AccessDenied appropriately
 */
class AppAuthenticatedNotAnonymous: AuthorizationManager<RequestAuthorizationContext> {

    override fun check(
        authentication: Supplier<Authentication>,
        `object`: RequestAuthorizationContext
    ): AuthorizationDecision {
        val auth = authentication.get()
        if (!auth.isAuthenticated) {
            throw NoTokenProvidedException()
        }
        return AuthorizationDecision(true)
    }
}
