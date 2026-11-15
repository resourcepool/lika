package li.lika.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import li.lika.security.exceptions.MessageAndErrorCodeSecurityException
import li.lika.security.models.AuthenticationExceptionMessage
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.AuthenticationFailureHandler

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
        return mapper.writeValueAsString(AuthenticationExceptionMessage(httpStatus.value(), "Access Denied", "security.access.denied"))
    }

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        ex: AuthenticationException
    ) {
        response.contentType = MediaType.APPLICATION_JSON.toString()
        val status = HttpStatus.valueOf((ex as? MessageAndErrorCodeSecurityException)?.status ?: 401)
        response.status = status.value()
        val error = getJsonMessageOnAuthFailed(status, ex, mapper)
        response.writer.write(error)
    }

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        ex: AccessDeniedException
    ) {
        response.contentType = MediaType.APPLICATION_JSON.toString()
        response.status = HttpStatus.FORBIDDEN.value()
        val error = getJsonMessageOnAccessDenied(HttpStatus.FORBIDDEN, ex, mapper)
        response.writer.write(error)
    }
}
