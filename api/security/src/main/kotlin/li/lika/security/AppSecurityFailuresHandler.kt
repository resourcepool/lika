package li.lika.security

import com.fasterxml.jackson.databind.ObjectMapper
import li.lika.security.exceptions.MessageAndErrorCodeSecurityException
import li.lika.security.exceptions.NoTokenProvidedException
import li.lika.security.models.AuthenticationExceptionMessage
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class AppSecurityFailuresHandler(private val mapper: ObjectMapper): AuthenticationFailureHandler,
    AccessDeniedHandler {

    private fun getJsonMessageOnAuthFailed(httpStatus: HttpStatus, ex: RuntimeException, mapper: ObjectMapper): String {
        val error: AuthenticationExceptionMessage?
        if (ex is MessageAndErrorCodeSecurityException) {
            error = AuthenticationExceptionMessage(httpStatus.value(), ex.message!!, ex.errorCode)
        } else {
            error = AuthenticationExceptionMessage(
                httpStatus.value(),
                ex.message ?: "An authentication error occurred"
            )
        }
        return mapper.writeValueAsString(error)
    }

    private fun getJsonMessageOnAccessDenied(httpStatus: HttpStatus, ex: RuntimeException, mapper: ObjectMapper): String {
        val error: AuthenticationExceptionMessage?
        if (ex is NoTokenProvidedException) {
            error = AuthenticationExceptionMessage(httpStatus.value(), ex.message?: "An authentication error occurred", NoTokenProvidedException.ERROR_CODE)
        } else {
            error = AuthenticationExceptionMessage(httpStatus.value(),"Access Denied", "security.access.denied")
        }
        return mapper.writeValueAsString(error)
    }

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        ex: AuthenticationException
    ) {
        response.contentType = MediaType.APPLICATION_JSON.toString()
        response.status = HttpStatus.UNAUTHORIZED.value()
        val error = getJsonMessageOnAuthFailed(HttpStatus.UNAUTHORIZED, ex, mapper)
        response.writer.write(error)
    }

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        ex: AccessDeniedException
    ) {
        response.contentType = MediaType.APPLICATION_JSON.toString()
        if (ex is NoTokenProvidedException) {
            response.status = HttpStatus.UNAUTHORIZED.value()
        } else {
            response.status = HttpStatus.FORBIDDEN.value()
        }
        val error = getJsonMessageOnAccessDenied(HttpStatus.valueOf(response.status), ex, mapper)
        response.writer.write(error)
    }
}
