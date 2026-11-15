package li.lika.core.services.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import li.lika.common.exceptions.IdentityProviderException
import li.lika.common.models.LikaClaims
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.URI
import java.time.Instant

/**
 * HTTP and token helpers shared by the external identity providers.
 */
open class ProviderSupport(protected val mapper: ObjectMapper, private val providerName: String) {

    protected val http: RestClient = RestClient.builder().build()

    protected fun form(url: String, values: Map<String, String?>): JsonNode {
        val body = LinkedMultiValueMap<String, String>()
        values.forEach { (k, v) -> if (v != null) body.add(k, v) }
        return call { http.post().uri(URI.create(url)).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body).retrieve().body(String::class.java) }
    }

    protected fun json(method: String, url: String, bearer: String?, body: Any? = null): JsonNode {
        return call {
            // Already encoded: a String would be encoded again as a URI template
            val spec = http.method(org.springframework.http.HttpMethod.valueOf(method)).uri(URI.create(url))
            bearer?.let { spec.header("Authorization", "Bearer $it") }
            if (body != null) spec.contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(body))
            spec.retrieve().body(String::class.java)
        }
    }

    /** Runs a call, mapping failures; 4xx answers are passed to [onClientError] first */
    protected fun call(request: () -> String?): JsonNode {
        return try {
            request()?.takeIf { it.isNotBlank() }?.let { mapper.readTree(it) } ?: mapper.createObjectNode()
        } catch (e: RestClientResponseException) {
            if (e.statusCode.is4xxClientError) onClientError(e.statusCode.value(), e.responseBodyAsString.takeIf { it.isNotBlank() }?.let { runCatching { mapper.readTree(it) }.getOrNull() })
            throw IdentityProviderException("$providerName answered ${e.statusCode.value()}", e)
        } catch (e: IdentityProviderException) {
            throw e
        } catch (e: li.lika.common.exceptions.MessageAndErrorCodeException) {
            throw e
        } catch (e: Exception) {
            throw IdentityProviderException("$providerName is unreachable", e)
        }
    }

    /** Throws a meaningful exception for a 4xx answer, or returns to let a generic one be thrown */
    protected open fun onClientError(status: Int, body: JsonNode?) {}

    protected fun verified(jwt: Jwt): VerifiedToken {
        val claim = jwt.getClaim<Any>(LikaClaims.CLAIM_NAME)
        val claims = when (claim) {
            is Map<*, *> -> IdentityProvider.fromClaimMap(claim)
            // Some mappers emit the JSON as a string
            is String -> runCatching { IdentityProvider.fromClaimMap(mapper.readValue(claim, Map::class.java)) }.getOrNull()
            else -> null
        }
        val authTime = (jwt.claims["auth_time"] as? Number)?.toLong()?.let { Instant.ofEpochSecond(it) } ?: jwt.claims["auth_time"] as? Instant
        return VerifiedToken(jwt.subject, claims, jwt.getClaimAsString("email")?.lowercase(), jwt.getClaim<Any>("email_verified") == true, authTime,
            jwt.getClaimAsString("sid"))
    }

    protected fun claimsJson(user: li.lika.common.models.User): String = mapper.writeValueAsString(IdentityProvider.toClaimMap(user.claims()))
}
