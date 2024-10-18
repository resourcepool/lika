package li.lika.security.models.security.utils

import li.lika.security.core.Headers
import li.lika.security.models.CredentialsType
import mu.KotlinLogging

object AuthenticationConverterUtils {
    private val logger = KotlinLogging.logger {}

    fun getCredentials(authHeaders: List<String>?, queryParams: List<String>?): Pair<CredentialsType, String>? {
        if (!authHeaders.isNullOrEmpty()) {
            return Pair(CredentialsType.AUTH_HEADER, Headers.decodeHeader(authHeaders[0]))
        }
        logger.debug("No Bearer Token. Checking query string.")
        if (!queryParams.isNullOrEmpty()) {
            return Pair(CredentialsType.QUERY_PARAM, queryParams[0])
        }
        logger.debug("No Access Token query param.")
        return null
    }
}
