package li.lika.security.exceptions

import org.springframework.security.core.AuthenticationException

open class MessageAndErrorCodeSecurityException(val errorCode: String, message: String, reason: Throwable? = null): AuthenticationException(message, reason) {
}
