package li.lika.security.models.security.utils

import li.lika.security.core.Headers
import li.lika.security.models.CredentialsType
import mu.KotlinLogging

object AuthenticationConverterUtils {
    private val logger = KotlinLogging.logger {}

    /** Authorization: Bearer first, then X-Api-Key, then the query parameter */
    fun getCredentials(authHeaders: List<String>?, apiKeyHeaders: List<String>?, queryParams: List<String>?): Pair<CredentialsType, String>? {
        if (!authHeaders.isNullOrEmpty()) {
            return Pair(CredentialsType.AUTH_HEADER, Headers.decodeHeader(authHeaders[0]))
        }
        logger.debug("No Bearer Token. Checking X-Api-Key.")
        apiKeyHeaders?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return Pair(CredentialsType.API_KEY_HEADER, it)
        }
        logger.debug("No X-Api-Key. Checking query string.")
        if (!queryParams.isNullOrEmpty()) {
            return Pair(CredentialsType.QUERY_PARAM, queryParams[0])
        }
        logger.debug("No Access Token query param.")
        return null
    }
}
