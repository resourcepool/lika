package li.lika.security.exceptions

import org.springframework.security.core.AuthenticationException

open class MessageAndErrorCodeSecurityException(val errorCode: String, message: String, reason: Throwable? = null, val status: Int = 401): AuthenticationException(message, reason) {
}
