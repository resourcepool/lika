package li.lika.security.core

object Headers {
    const val AUTH_HEADER_NAME = "Authorization"
    const val AUTH_HEADER_PREFIX = "Bearer "
    /** API key sent as is, for clients that cannot send an Authorization header easily */
    const val API_KEY_HEADER_NAME = "X-Api-Key"

    fun decodeHeader(value: String): String {
        return value.substring(AUTH_HEADER_PREFIX.length)
    }
}
