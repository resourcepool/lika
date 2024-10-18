package li.lika.security.core

object Headers {
    const val CLIENT_HEADER_NAME = "X-Client"
    const val AUTH_HEADER_NAME = "Authorization"
    const val AUTH_HEADER_PREFIX = "Bearer "

    fun decodeHeader(value: String): String {
        return value.substring(AUTH_HEADER_PREFIX.length)
    }

    fun encodeToken(token: String): String {
        return AUTH_HEADER_PREFIX + token
    }
}
