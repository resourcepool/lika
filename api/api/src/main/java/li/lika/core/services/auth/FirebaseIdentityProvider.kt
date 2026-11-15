package li.lika.core.services.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jwt.JWTParser
import li.lika.common.exceptions.IdentityProviderException
import li.lika.common.exceptions.InvalidCredentialsException
import li.lika.common.exceptions.TooManyAttemptsException
import li.lika.common.exceptions.UserDisabledException
import li.lika.common.models.AuthProviderType
import li.lika.common.models.SsoConfig
import li.lika.common.models.User
import li.lika.core.props.AuthProperties
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * Users sign in with Firebase Authentication: email and password typed in Lika, and Google when its OAuth client
 * is configured. Rights are written as the custom claim `lika`, through the Identity Toolkit API with a service account.
 */
@Component
@ConditionalOnProperty(name = ["lika.auth.provider"], havingValue = "firebase")
class FirebaseIdentityProvider(props: AuthProperties, mapper: ObjectMapper) : ProviderSupport(mapper, "Firebase"), IdentityProvider {
    private val logger = KotlinLogging.logger {}
    private val fb = props.firebase

    override val type = AuthProviderType.FIREBASE
    override val passwordSignIn = true

    private val emulator: String? = fb.emulatorHost?.takeIf { it.isNotBlank() }

    private data class ServiceAccount(val email: String, val key: RSAPrivateKey, val tokenUri: String)

    init {
        val missing = listOf("LIKA_FIREBASE_PROJECT_ID" to fb.projectId, "LIKA_FIREBASE_API_KEY" to fb.apiKey).filter { it.second.isNullOrBlank() }.map { it.first }
        check(missing.isEmpty()) { "LIKA_AUTH_PROVIDER=firebase needs ${missing.joinToString(", ")}" }
        check(emulator != null || !fb.serviceAccount.isNullOrBlank()) { "LIKA_AUTH_PROVIDER=firebase needs LIKA_FIREBASE_SERVICE_ACCOUNT (path to, or content of, a service account key)" }
        check(fb.googleClientId.isNullOrBlank() == fb.googleClientSecret.isNullOrBlank()) { "Set both LIKA_FIREBASE_GOOGLE_CLIENT_ID and LIKA_FIREBASE_GOOGLE_CLIENT_SECRET, or neither" }
        if (emulator != null) logger.warn("Using the Firebase Auth emulator at {}: tokens are not signed. Never in production.", emulator)
    }

    // After the checks above: properties are initialized in declaration order
    private val projectId: String = fb.projectId!!
    private val apiKey: String = fb.apiKey!!
    private val identityToolkit: String = if (emulator != null) "http://$emulator/identitytoolkit.googleapis.com/v1" else "https://identitytoolkit.googleapis.com/v1"
    private val secureToken: String = if (emulator != null) "http://$emulator/securetoken.googleapis.com/v1" else "https://securetoken.googleapis.com/v1"
    private val serviceAccount: ServiceAccount? = fb.serviceAccount?.takeIf { it.isNotBlank() }?.let { readServiceAccount(it) }

    private fun readServiceAccount(value: String): ServiceAccount {
        val json = mapper.readTree(if (value.trimStart().startsWith("{")) value else Files.readString(Path.of(value)))
        val pem = json["private_key"].asText().replace(Regex("-----[A-Z ]+-----|\\s"), "")
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem))) as RSAPrivateKey
        return ServiceAccount(json["client_email"].asText(), key, json.path("token_uri").asText("https://oauth2.googleapis.com/token"))
    }

    private val decoder: NimbusJwtDecoder by lazy {
        NimbusJwtDecoder.withJwkSetUri(JWKS).build().also {
            it.setJwtValidator(DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer("https://securetoken.google.com/$projectId"),
                JwtClaimValidator<List<String>>("aud") { aud -> aud?.contains(projectId) == true }
            ))
        }
    }

    override fun verify(token: String): VerifiedToken {
        try {
            if (emulator != null) return verifyEmulatorToken(token)
            return verified(decoder.decode(token))
        } catch (e: JwtException) {
            throw InvalidCredentialsException("Invalid or expired token")
        }
    }

    /** The emulator signs nothing: only the claims are checked */
    private fun verifyEmulatorToken(token: String): VerifiedToken {
        val claims = runCatching { JWTParser.parse(token).jwtClaimsSet }.getOrNull() ?: throw InvalidCredentialsException("Invalid token")
        if (claims.issuer != "https://securetoken.google.com/$projectId" || projectId !in claims.audience || claims.expirationTime.before(Date())) {
            throw InvalidCredentialsException("Invalid or expired token")
        }
        val jwt = Jwt.withTokenValue(token).header("alg", "none").claims { it.putAll(claims.claims) }
            .issuedAt(claims.issueTime?.toInstant()).expiresAt(claims.expirationTime.toInstant()).build()
        return verified(jwt)
    }

    override fun sso(): SsoConfig? = fb.googleClientId?.takeIf { it.isNotBlank() }?.let {
        SsoConfig("Google", "https://accounts.google.com/o/oauth2/v2/auth", it, "openid email profile", mapOf("prompt" to "select_account"))
    }

    // --- Sessions

    override fun login(email: String, password: String): ProviderSession {
        val r = json("POST", "$identityToolkit/accounts:signInWithPassword?key=$apiKey", null, mapOf("email" to email, "password" to password, "returnSecureToken" to true))
        return session(r["idToken"].asText(), r["refreshToken"].asText(), r["expiresIn"].asLong())
    }

    override fun refresh(refreshToken: String): ProviderSession {
        val r = form("$secureToken/token?key=$apiKey", mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken))
        return session(r["id_token"].asText(), r["refresh_token"].asText(), r["expires_in"].asLong())
    }

    /** Google sign-in: the code gives a Google ID token, which Firebase turns into a session */
    override fun exchangeCode(code: String, codeVerifier: String, redirectUri: String): ProviderSession {
        val google = form("https://oauth2.googleapis.com/token", mapOf(
            "grant_type" to "authorization_code", "code" to code, "code_verifier" to codeVerifier, "redirect_uri" to redirectUri,
            "client_id" to fb.googleClientId, "client_secret" to fb.googleClientSecret
        ))
        val r = json("POST", "$identityToolkit/accounts:signInWithIdp?key=$apiKey", null, mapOf(
            "requestUri" to redirectUri,
            "postBody" to "id_token=${google["id_token"].asText()}&providerId=google.com",
            "returnSecureToken" to true
        ))
        return session(r["idToken"].asText(), r["refreshToken"].asText(), r["expiresIn"].asLong())
    }

    private fun session(idToken: String, refreshToken: String, expiresIn: Long) = ProviderSession(idToken, refreshToken, expiresIn, verify(idToken))

    override fun onClientError(status: Int, body: JsonNode?) {
        val message = body?.path("error")?.let { if (it.isTextual) it.asText() else it.path("message").asText() } ?: return
        when {
            message.startsWith("INVALID_LOGIN_CREDENTIALS") || message.startsWith("INVALID_PASSWORD") || message.startsWith("EMAIL_NOT_FOUND") ->
                throw InvalidCredentialsException("Wrong email or password")
            message.startsWith("USER_DISABLED") -> throw UserDisabledException()
            message.startsWith("TOO_MANY_ATTEMPTS_TRY_LATER") -> throw TooManyAttemptsException()
            message.startsWith("TOKEN_EXPIRED") || message.startsWith("INVALID_REFRESH_TOKEN") || message.startsWith("USER_NOT_FOUND") ->
                throw InvalidCredentialsException("Session expired")
            message.startsWith("WEAK_PASSWORD") -> throw li.lika.common.exceptions.WeakPasswordException()
            else -> throw IdentityProviderException("Firebase: $message")
        }
    }

    // --- Accounts, through the admin API

    override fun findAccount(email: String): String? {
        val r = admin("accounts:lookup", mapOf("email" to listOf(email)))
        return r.path("users").firstOrNull()?.get("localId")?.asText()
    }

    override fun createAccount(user: User, password: String?): String {
        findAccount(user.email)?.let { return it }
        val body = mutableMapOf<String, Any>("email" to user.email, "displayName" to user.name, "emailVerified" to true)
        password?.let { body["password"] = it }
        val id = admin("accounts", body)["localId"].asText()
        pushClaims(user.copy(externalId = id))
        return id
    }

    override fun setPassword(externalId: String, password: String) {
        admin("accounts:update", mapOf("localId" to externalId, "password" to password))
    }

    override fun pushClaims(user: User) {
        val id = user.externalId ?: return
        val claims = mapper.writeValueAsString(mapOf("lika" to IdentityProvider.toClaimMap(user.claims())))
        if (claims.length > CUSTOM_CLAIMS_LIMIT) {
            throw IllegalArgumentException("Too many domains for Firebase: custom claims are limited to $CUSTOM_CLAIMS_LIMIT characters. Make the user admin, or give fewer domains.")
        }
        admin("accounts:update", mapOf("localId" to id, "customAttributes" to claims))
    }

    override fun clearClaims(externalId: String) {
        admin("accounts:update", mapOf("localId" to externalId, "customAttributes" to "{}"))
    }

    /** Firebase sends its own reset email, when Lika cannot send one */
    override fun sendPasswordResetEmail(email: String): Boolean {
        json("POST", "$identityToolkit/accounts:sendOobCode?key=$apiKey", null, mapOf("requestType" to "PASSWORD_RESET", "email" to email))
        return true
    }

    private fun admin(path: String, body: Any): JsonNode = json("POST", "$identityToolkit/projects/$projectId/$path", accessToken(), body)

    @Volatile
    private var token: Pair<String, Instant>? = null

    /** OAuth access token of the service account; the emulator accepts any "owner" */
    private fun accessToken(): String {
        if (emulator != null) return "owner"
        token?.takeIf { it.second.isAfter(Instant.now().plusSeconds(60)) }?.let { return it.first }
        val sa = serviceAccount!!
        val now = Instant.now()
        val assertion = JWT.create()
            .withIssuer(sa.email)
            .withAudience(sa.tokenUri)
            .withClaim("scope", "https://www.googleapis.com/auth/identitytoolkit https://www.googleapis.com/auth/cloud-platform")
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plusSeconds(3600)))
            .sign(Algorithm.RSA256(null, sa.key))
        val r = form(sa.tokenUri, mapOf("grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion" to assertion))
        return r["access_token"].asText().also { token = it to now.plusSeconds(r["expires_in"].asLong()) }
    }

    companion object {
        const val JWKS = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
        const val CUSTOM_CLAIMS_LIMIT = 1000
    }
}
