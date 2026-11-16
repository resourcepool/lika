package li.lika.core.services.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.annotation.PostConstruct
import li.lika.common.exceptions.IdentityProviderException
import li.lika.common.exceptions.InvalidCredentialsException
import li.lika.common.models.AuthProviderType
import li.lika.common.models.SsoConfig
import li.lika.common.models.User
import li.lika.core.props.AuthProperties
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException
import java.net.URLEncoder
import java.time.Instant

/**
 * Users sign in with a Keycloak realm (authorization code with PKCE).
 *
 * Rights are written to the user attribute `lika` (or LIKA_CLAIM_NAME), which a protocol mapper of the client
 * puts in the tokens as the claim of the same name. Lika declares the attribute in the realm's user profile and
 * creates or updates the mapper by itself.
 */
@Component
@ConditionalOnProperty(name = ["lika.auth.provider"], havingValue = "keycloak")
class KeycloakIdentityProvider(props: AuthProperties, mapper: ObjectMapper) : ProviderSupport(mapper, "Keycloak", props.claimName), IdentityProvider {
    private val logger = KotlinLogging.logger {}
    private val kc = props.keycloak

    /** User attribute holding the rights, named like the claim */
    private val attribute = claimName

    override val type = AuthProviderType.KEYCLOAK
    override val passwordSignIn = false

    init {
        val missing = listOf("LIKA_KEYCLOAK_URL" to kc.url, "LIKA_KEYCLOAK_REALM" to kc.realm, "LIKA_KEYCLOAK_CLIENT_ID" to kc.clientId, "LIKA_KEYCLOAK_CLIENT_SECRET" to kc.clientSecret)
            .filter { it.second.isNullOrBlank() }.map { it.first }
        check(missing.isEmpty()) { "LIKA_AUTH_PROVIDER=keycloak needs ${missing.joinToString(", ")}" }
    }

    // After the check above: properties are initialized in declaration order
    private val realmUrl: String = "${kc.url!!.trimEnd('/')}/realms/${kc.realm}"
    private val adminUrl: String = "${kc.url!!.trimEnd('/')}/admin/realms/${kc.realm}"

    private data class Endpoints(val issuer: String, val authorization: String, val token: String, val logout: String, val jwks: String)

    @Volatile
    private var endpoints: Endpoints? = null

    @Volatile
    private var decoder: NimbusJwtDecoder? = null

    @Volatile
    private var serviceToken: Pair<String, Instant>? = null

    @Volatile
    private var setupDone = false

    /** Keycloak may start after Lika: setup is retried until it succeeds */
    @PostConstruct
    fun trySetup() {
        try {
            ensureSetup()
        } catch (e: Exception) {
            logger.warn("Keycloak is not ready yet ({}); setup will be retried on first use", e.message)
        }
    }

    private fun endpoints(): Endpoints = endpoints ?: synchronized(this) {
        endpoints ?: discover().also { endpoints = it }
    }

    private fun discover(): Endpoints {
        val d = json("GET", "$realmUrl/.well-known/openid-configuration", null)
        return Endpoints(d["issuer"].asText(), d["authorization_endpoint"].asText(), d["token_endpoint"].asText(), d["end_session_endpoint"].asText(), d["jwks_uri"].asText())
    }

    private fun decoder(): NimbusJwtDecoder = decoder ?: synchronized(this) {
        decoder ?: NimbusJwtDecoder.withJwkSetUri(endpoints().jwks).build().also { d ->
            // Tokens of the Lika client only: Keycloak sets azp to the client the token was issued to
            val authorizedParty = org.springframework.security.oauth2.core.OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> { jwt ->
                if (jwt.getClaimAsString("azp") == kc.clientId) OAuth2TokenValidatorResult.success()
                else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Token issued to another client", null))
            }
            d.setJwtValidator(DelegatingOAuth2TokenValidator(JwtValidators.createDefaultWithIssuer(endpoints().issuer), authorizedParty))
            decoder = d
        }
    }

    override fun verify(token: String): VerifiedToken {
        ensureSetup()
        return try {
            verified(decoder().decode(token))
        } catch (e: JwtException) {
            throw InvalidCredentialsException("Invalid or expired token")
        }
    }

    override fun sso() = SsoConfig(kc.label, endpoints().authorization, kc.clientId!!, "openid email profile")

    override fun accountUrl() = "$realmUrl/account"

    // --- Sessions

    override fun exchangeCode(code: String, codeVerifier: String, redirectUri: String): ProviderSession =
        session(tokenRequest(mapOf("grant_type" to "authorization_code", "code" to code, "code_verifier" to codeVerifier, "redirect_uri" to redirectUri)))

    override fun refresh(refreshToken: String): ProviderSession =
        session(tokenRequest(mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken)))

    override fun logout(refreshToken: String) {
        runCatching { tokenRequest(mapOf("refresh_token" to refreshToken), endpoints().logout) }
            .onFailure { logger.debug("Keycloak logout failed: {}", it.message) }
    }

    private fun tokenRequest(values: Map<String, String>, url: String = endpoints().token): JsonNode =
        form(url, values + mapOf("client_id" to kc.clientId!!, "client_secret" to kc.clientSecret!!))

    private fun session(tokens: JsonNode): ProviderSession {
        val access = tokens["access_token"].asText()
        return ProviderSession(access, tokens["refresh_token"].asText(), tokens["expires_in"].asLong(), verify(access))
    }

    override fun onClientError(status: Int, body: JsonNode?) {
        // invalid_grant: expired or revoked code or refresh token
        if (body?.path("error")?.asText() == "invalid_grant") throw InvalidCredentialsException("Session expired")
    }

    // --- Accounts

    override fun findAccount(email: String): String? {
        val users = admin("GET", "/users?exact=true&email=${URLEncoder.encode(email, Charsets.UTF_8)}")
        return users.firstOrNull { it.path("email").asText().equals(email, ignoreCase = true) }?.get("id")?.asText()
    }

    override fun createAccount(user: User, password: String?): String {
        val first = user.name.substringBefore(' ').ifBlank { user.email.substringBefore('@') }
        val last = user.name.substringAfter(' ', "").ifBlank { first }
        val rep = mutableMapOf<String, Any>(
            "username" to user.email, "email" to user.email, "emailVerified" to true, "enabled" to true,
            "firstName" to first, "lastName" to last,
            "attributes" to mapOf(attribute to listOf(claimsJson(user)))
        )
        password?.let { rep["credentials"] = listOf(mapOf("type" to "password", "value" to it, "temporary" to false)) }
        try {
            ensureSetup()
            http.post().uri(java.net.URI.create("$adminUrl/users")).header("Authorization", "Bearer ${serviceToken()}")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(rep))
                .retrieve().toBodilessEntity()
        } catch (e: RestClientResponseException) {
            if (e.statusCode.value() != 409) throw IdentityProviderException("Keycloak refused to create ${user.email}: ${e.responseBodyAsString}", e)
        }
        return findAccount(user.email) ?: throw IdentityProviderException("Keycloak did not create ${user.email}")
    }

    override fun setPassword(externalId: String, password: String) {
        admin("PUT", "/users/$externalId/reset-password", mapOf("type" to "password", "value" to password, "temporary" to false))
    }

    override fun pushClaims(user: User) {
        user.externalId?.let { setAttribute(it, claimsJson(user)) }
    }

    override fun clearClaims(externalId: String) = setAttribute(externalId, null)

    /** Keycloak access tokens carry no auth_time, but the session id (sid) of the user */
    override fun sessionIds(externalId: String): List<String> = admin("GET", "/users/$externalId/sessions").mapNotNull { it.get("id")?.asText() }

    private fun setAttribute(externalId: String, value: String?) {
        // Full representation: Keycloak replaces the attributes with the ones sent
        val rep = admin("GET", "/users/$externalId") as ObjectNode
        val attributes = (rep.get("attributes") as? ObjectNode) ?: rep.putObject("attributes")
        if (value == null) attributes.remove(attribute) else attributes.set<ArrayNode>(attribute, mapper.createArrayNode().add(value))
        admin("PUT", "/users/$externalId", rep)
    }

    // --- Admin API, through the service account of the client

    private fun admin(method: String, path: String, body: Any? = null): JsonNode {
        ensureSetup()
        return json(method, "$adminUrl$path", serviceToken(), body)
    }

    private fun serviceToken(): String {
        serviceToken?.takeIf { it.second.isAfter(Instant.now().plusSeconds(30)) }?.let { return it.first }
        val tokens = tokenRequest(mapOf("grant_type" to "client_credentials"))
        return tokens["access_token"].asText().also { serviceToken = it to Instant.now().plusSeconds(tokens["expires_in"].asLong()) }
    }

    /**
     * Declares the `lika` attribute in the user profile (only admins see and edit it), and the mapper putting it
     * in the tokens of the client. Idempotent.
     */
    private fun ensureSetup() {
        if (setupDone) return
        synchronized(this) {
            if (setupDone) return
            val token = serviceToken()
            // User profile (Keycloak 24+): undeclared attributes are dropped
            val profile = runCatching { json("GET", "$adminUrl/users/profile", token) }.getOrNull()
            if (profile != null && profile.path("attributes").none { it.path("name").asText() == attribute }) {
                val declaration = mapper.createObjectNode()
                    .put("name", attribute)
                    .put("displayName", "Lika rights")
                    .put("multivalued", false)
                declaration.putObject("permissions").also {
                    it.putArray("view").add("admin")
                    it.putArray("edit").add("admin")
                }
                (profile.get("attributes") as ArrayNode).add(declaration)
                json("PUT", "$adminUrl/users/profile", token, profile)
                logger.info("Declared the attribute {} in the user profile of realm {}", attribute, kc.realm)
            }
            val client = json("GET", "$adminUrl/clients?clientId=${URLEncoder.encode(kc.clientId, Charsets.UTF_8)}", token).firstOrNull()
                ?: throw IdentityProviderException("Keycloak client ${kc.clientId} not found in realm ${kc.realm}")
            val clientUuid = client["id"].asText()
            val mappers = json("GET", "$adminUrl/clients/$clientUuid/protocol-mappers/models", token)
            val config = mapOf(
                "user.attribute" to attribute,
                "claim.name" to claimName,
                "jsonType.label" to "JSON",
                "access.token.claim" to "true",
                "id.token.claim" to "true",
                "userinfo.token.claim" to "true",
                "introspection.token.claim" to "true",
                "multivalued" to "false"
            )
            val existing = mappers.firstOrNull { it.path("name").asText() == MAPPER }
            if (existing == null) {
                json("POST", "$adminUrl/clients/$clientUuid/protocol-mappers/models", token, mapOf(
                    "name" to MAPPER,
                    "protocol" to "openid-connect",
                    "protocolMapper" to "oidc-usermodel-attribute-mapper",
                    "config" to config
                ))
                logger.info("Created the protocol mapper {} on client {}, claim {}", MAPPER, kc.clientId, claimName)
            } else if (existing.path("config").path("user.attribute").asText() != attribute || existing.path("config").path("claim.name").asText() != claimName) {
                // LIKA_CLAIM_NAME changed: the mapper follows
                val updated = (existing as ObjectNode).deepCopy()
                val updatedConfig = (updated.get("config") as? ObjectNode) ?: updated.putObject("config")
                config.forEach { (k, v) -> updatedConfig.put(k, v) }
                json("PUT", "$adminUrl/clients/$clientUuid/protocol-mappers/models/${existing["id"].asText()}", token, updated)
                logger.info("Updated the protocol mapper {} on client {}: claim {}", MAPPER, kc.clientId, claimName)
            }
            setupDone = true
        }
    }

    companion object {
        const val MAPPER = "lika rights"
    }
}
