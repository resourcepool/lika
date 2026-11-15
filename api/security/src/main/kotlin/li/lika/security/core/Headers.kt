package li.lika.security.core

object Headers {
    const val AUTH_HEADER_NAME = "Authorization"
    const val AUTH_HEADER_PREFIX = "Bearer "

    fun decodeHeader(value: String): String {
        return value.substring(AUTH_HEADER_PREFIX.length)
    }
}
