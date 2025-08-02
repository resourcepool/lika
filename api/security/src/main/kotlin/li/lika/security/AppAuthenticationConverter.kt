package li.lika.security

import jakarta.servlet.http.HttpServletRequest
import li.lika.security.core.Headers.AUTH_HEADER_NAME
import li.lika.security.core.Params.ACCESS_TOKEN
import li.lika.security.models.DummyAuthentication
import li.lika.security.models.security.utils.AuthenticationConverterUtils
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationConverter

/**
 * Encode token and decode header from frontend
 */
class AppAuthenticationConverter :
    AuthenticationConverter {
    private val logger = LoggerFactory.getLogger(AuthenticationConverter::class.java)

    override fun convert(request: HttpServletRequest): Authentication {
        val authHeaders = request.getHeaders(AUTH_HEADER_NAME)?.toList()
        val queryParams = request.getParameterValues(ACCESS_TOKEN)?.toList()
        val credentials = AuthenticationConverterUtils.getCredentials(authHeaders, queryParams)

        if (credentials == null) {
            logger.debug("Exhausted available authentications. No credentials provided. Invalid.")
            return DummyAuthentication.invalid()
        }
        logger.debug("Token specified. Will proceed with authentication")
        return DummyAuthentication.of(credentials.second)
    }
}
