package li.lika.security

import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class AppAuthenticationSuccessHandler : AuthenticationSuccessHandler {
    /**
     * A successful authentication object us used to create a JWT object and
     * added in the authorization header of the current WebExchange.
     * This is only done when authentication is not anonymous!
     *
     */
    override fun onAuthenticationSuccess(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {

    }
}
