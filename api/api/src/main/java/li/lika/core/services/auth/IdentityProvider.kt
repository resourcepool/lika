package li.lika.core.services.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import li.lika.common.exceptions.InvalidCredentialsException
import li.lika.common.exceptions.UnsupportedByProviderException
import li.lika.common.models.AuthProviderType
import li.lika.common.models.DomainRole
import li.lika.common.models.LikaClaims
import li.lika.common.models.SsoConfig
import li.lika.common.models.User
import li.lika.core.props.AuthProperties
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date

/** What a valid user token says */
data class VerifiedToken(
    /** Lika user id for the local provider, provider user id otherwise */
    val subject: String,
    /** The `lika` claim, null when the provider does not carry it yet (new account) */
    val claims: LikaClaims?,
    val email: String? = null,
    val emailVerified: Boolean = false,
    /** When the user signed in, kept across token renewals */
    val authTime: Instant? = null,
    /** Session at the provider (sid), kept across token renewals */
    val sessionId: String? = null
)

/** Tokens of a session opened with an external identity provider */
data class ProviderSession(val accessToken: String, val refreshToken: String, val expiresIn: Long, val verified: VerifiedToken)

/**
 * The identity provider users sign in with. Exactly one is active (LIKA_AUTH_PROVIDER).
 *
 * Lika keeps its own copy of each user and runs their lifecycle: invitations, rights, password reset links,
 * disabling, sessions. The provider holds credentials, issues tokens, and receives the rights as the `lika` claim.
 * Lika never disables nor deletes accounts at the provider, which other applications may share.
 */
interface IdentityProvider {
    val type: AuthProviderType

    /** Users type their email and password in the sign-in form of Lika */
    val passwordSignIn: Boolean

    /** @throws InvalidCredentialsException when the token is not valid */
    fun verify(token: String): VerifiedToken

    fun sso(): SsoConfig? = null

    /** Where users manage their own password, when Lika cannot */
    fun accountUrl(): String? = null

    // --- Sessions (external providers)

    fun login(email: String, password: String): ProviderSession = unsupported()
    fun refresh(refreshToken: String): ProviderSession = unsupported()
    fun logout(refreshToken: String) {}
    fun exchangeCode(code: String, codeVerifier: String, redirectUri: String): ProviderSession = unsupported()

    // --- Accounts (external providers)

    /** @return the provider id of the account with this email, if any */
    fun findAccount(email: String): String? = null

    /** Creates the account of [user], with [password] if given. @return its provider id */
    fun createAccount(user: User, password: String?): String = unsupported()

    fun setPassword(externalId: String, password: String): Unit = unsupported()

    /** Writes the rights of [user] to its account, as the `lika` claim */
    fun pushClaims(user: User) {}

    /** Removes the `lika` claim: the user lost access to Lika */
    fun clearClaims(externalId: String) {}

    /** Ids of the user's current sessions at the provider, for providers whose tokens carry no auth_time */
    fun sessionIds(externalId: String): List<String> = emptyList()

    /** Lets the provider email a password reset link itself. @return whether it did */
    fun sendPasswordResetEmail(email: String): Boolean = false

    private fun unsupported(): Nothing = throw UnsupportedByProviderException("Not available with the ${type.name.lowercase()} identity provider")

    companion object {
        fun toClaimMap(claims: LikaClaims): Map<String, Any> = mapOf(
            "v" to claims.v,
            "admin" to claims.admin,
            "domains" to claims.domains.mapValues { it.value.value }
        )

        /** Reads the `lika` claim, whichever provider issued it */
        fun fromClaimMap(map: Map<*, *>): LikaClaims {
            val v = (map["v"] as? Number)?.toLong() ?: 0
            val admin = map["admin"] == true
            val domains = (map["domains"] as? Map<*, *>).orEmpty()
                .mapNotNull { (id, role) -> (role as? String)?.let { id.toString() to DomainRole.parse(it) } }
                .toMap()
            return LikaClaims(v, admin, domains)
        }
    }
}

/**
 * Lika is the identity provider: passwords are stored in its database and it signs the access tokens.
 */
@Component
@ConditionalOnProperty(name = ["lika.auth.provider"], havingValue = "local", matchIfMissing = true)
class LocalIdentityProvider(private val props: AuthProperties) : IdentityProvider {
    private val logger = KotlinLogging.logger {}

    override val type = AuthProviderType.LOCAL
    override val passwordSignIn = true

    private val algorithm: Algorithm by lazy {
        val secret = props.jwtSecret?.takeIf { it.isNotBlank() } ?: run {
            logger.warn("LIKA_JWT_SECRET is not set: using a random secret. Sessions survive restarts, but every API instance needs the same secret.")
            Secrets.random(64)
        }
        require(secret.length >= 32) { "LIKA_JWT_SECRET must be at least 32 characters long" }
        Algorithm.HMAC256(secret)
    }

    private val verifier by lazy { JWT.require(algorithm).withIssuer(ISSUER).build() }

    fun issueAccessToken(user: User, now: Instant = Instant.now()): String {
        return JWT.create()
            .withIssuer(ISSUER)
            .withSubject(user.id)
            .withClaim("email", user.email)
            .withClaim("name", user.name)
            .withClaim(LikaClaims.CLAIM_NAME, IdentityProvider.toClaimMap(user.claims()))
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plus(props.accessTokenTtl)))
            .sign(algorithm)
    }

    override fun verify(token: String): VerifiedToken {
        val jwt = try {
            verifier.verify(token)
        } catch (e: JWTVerificationException) {
            throw InvalidCredentialsException("Invalid or expired token")
        }
        val claim = jwt.getClaim(LikaClaims.CLAIM_NAME).asMap() ?: throw InvalidCredentialsException("Token without rights")
        return VerifiedToken(jwt.subject, IdentityProvider.fromClaimMap(claim), jwt.getClaim("email").asString(), true, jwt.issuedAtAsInstant)
    }

    companion object {
        const val ISSUER = "lika"
    }
}
