package li.lika.common.models

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

enum class UserStatus {
    /** Invited, has not signed in yet */
    @JsonProperty("invited") INVITED,
    @JsonProperty("active") ACTIVE,
    /** Cannot sign in; existing sessions are revoked */
    @JsonProperty("disabled") DISABLED
}

/**
 * A person signing in to Lika. With an external identity provider, this is Lika's copy of the user,
 * the provider holding the credentials and the rights being written to it as claims.
 */
data class User(
    val id: String,
    /** Always lower case */
    val email: String,
    val name: String,
    val status: UserStatus,
    val admin: Boolean = false,
    val domains: Map<String, DomainRole> = emptyMap(),
    /** Bumped whenever the rights change or the sessions are revoked: tokens carrying an older version are refused */
    val version: Long = 1,
    /** BCrypt hash, local provider only */
    @get:JsonIgnore
    val passwordHash: String? = null,
    /** Id of the user at the identity provider. Null for the local provider. */
    val externalId: String? = null,
    /** Id of the user who sent the invitation */
    val invitedBy: String? = null,
    val createdAt: Instant = Instant.now(),
    val lastLoginAt: Instant? = null,
    /**
     * Sessions opened before are refused: signed out everywhere, disabled, or password changed.
     * With an external identity provider, Lika enforces it on the provider's tokens.
     */
    @get:JsonIgnore
    val sessionsNotBefore: Instant? = null
) {
    fun claims(): LikaClaims = LikaClaims(version, admin, domains)

    val hasPassword: Boolean
        get() = !passwordHash.isNullOrBlank()
}

enum class UserTokenType {
    INVITATION,
    PASSWORD_RESET,
    /** Local provider: renews the access token of a session */
    REFRESH,
    /** Session of the identity provider (sid) refused by Lika: the user was signed out everywhere */
    REVOKED_SESSION
}

/**
 * A single-use secret sent to or held by a user. Only its SHA-256 hash is stored.
 */
data class UserToken(
    val id: String,
    val userId: String,
    val type: UserTokenType,
    val tokenHash: String,
    val createdAt: Instant,
    val expiresAt: Instant
) {
    fun isExpired(now: Instant = Instant.now()): Boolean = !now.isBefore(expiresAt)
}

/**
 * Credentials of an application, which belong to no user and outlive the person who created them,
 * or a user API key of a person ([userId] set): one per application they connect, acting with the user's
 * current rights, and only accepted by the MCP server.
 */
data class ApiKey(
    val id: String,
    /** The application using the key */
    val name: String,
    /** Every right, like an admin user. Only admins create such keys. */
    val admin: Boolean = false,
    /** Viewer or editor only: keys never manage */
    val domains: Map<String, DomainRole> = emptyMap(),
    /** SHA-256 of the secret, which is shown once at creation */
    @get:JsonIgnore
    val secretHash: String,
    /** First characters of the secret, to recognize it */
    val prefix: String,
    /** Id of the user who created the key, null for keys migrated from accounts */
    val createdBy: String? = null,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant? = null,
    val lastUsedAt: Instant? = null,
    /** Owner of a user API key. Its rights are the user's, not [admin] and [domains]. */
    val userId: String? = null
) {
    val userKey: Boolean
        get() = userId != null

    fun claims(): LikaClaims = LikaClaims(0, admin, domains)

    fun isExpired(now: Instant = Instant.now()): Boolean = expiresAt != null && !now.isBefore(expiresAt)
}
