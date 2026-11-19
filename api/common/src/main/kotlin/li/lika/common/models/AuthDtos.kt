package li.lika.common.models

import com.fasterxml.jackson.annotation.JsonProperty

enum class AuthProviderType {
    @JsonProperty("local") LOCAL,
    @JsonProperty("firebase") FIREBASE,
    @JsonProperty("keycloak") KEYCLOAK
}

/**
 * Single sign-on through the identity provider: authorization code flow with PKCE, started by the web app.
 * The code is exchanged by the API (POST /api/auth/sso/token), which holds the client secret.
 */
data class SsoConfig(
    /** Shown as "Sign in with <label>" */
    val label: String,
    val authorizationEndpoint: String,
    val clientId: String,
    val scope: String,
    /** Extra query parameters of the authorization request */
    val params: Map<String, String> = emptyMap()
)

enum class PasswordReset {
    /** Users get a reset link by email */
    @JsonProperty("email") EMAIL,
    /** No email can be sent: an admin sends them a link */
    @JsonProperty("admin") ADMIN,
    /** On the sign-in page of the identity provider */
    @JsonProperty("provider") PROVIDER
}

/** Public: tells the web app how to sign users in */
data class AuthConfig(
    val provider: AuthProviderType,
    /** No admin exists yet: the first one is created with the setup token printed in the API logs */
    val setupRequired: Boolean,
    /** Invitations and password resets are emailed; otherwise their links must be passed on by hand */
    val mailEnabled: Boolean,
    /** URL of the MCP server, for AI assistants. Null when the public URL of the API is not configured. */
    val mcpUrl: String? = null,
    /** Email and password typed in the sign-in form of Lika */
    val passwordSignIn: Boolean = true,
    val sso: SsoConfig? = null,
    /** Where users manage their password, when Lika cannot */
    val accountUrl: String? = null,
    val passwordReset: PasswordReset = PasswordReset.ADMIN
)

/** Authorization code of the identity provider, to exchange for a session */
data class SsoTokenRequest(var code: String = "", var codeVerifier: String = "", var redirectUri: String = "")

/** [password] may be empty when the identity provider already has an account for [email] */
data class SetupRequest(var setupToken: String = "", var email: String = "", var name: String = "", var password: String = "")

data class LoginRequest(var email: String = "", var password: String = "")

data class RefreshRequest(var refreshToken: String = "")

/** Tokens of a session opened with the local provider */
data class AuthTokens(
    val accessToken: String,
    /** Seconds */
    val expiresIn: Long,
    val refreshToken: String,
    val user: User
)

/**
 * @param accountExists the identity provider already knows this email: the invited user signs in with it
 *   instead of choosing a password
 */
data class InvitationInfo(val email: String, val name: String, val accountExists: Boolean = false)

data class AcceptInvitationRequest(var password: String = "", var name: String? = null)

data class ForgotPasswordRequest(var email: String = "")

data class ResetPasswordRequest(var token: String = "", var password: String = "")

data class ChangePasswordRequest(var currentPassword: String = "", var newPassword: String = "")

data class ProfileUpdate(var name: String = "")

data class InvitationRequest(
    var email: String = "",
    var name: String = "",
    var admin: Boolean = false,
    var domains: Map<String, DomainRole> = emptyMap()
)

data class AccessUpdate(
    /** Ignored unless the caller is an admin */
    var admin: Boolean? = null,
    /** Role per domain. A manager only changes the domains they manage; null removes the role. */
    var domains: Map<String, DomainRole?> = emptyMap()
)

data class UserUpdate(var name: String? = null, var email: String? = null)

/**
 * @param link invitation or password reset link, to pass on by hand when no email was sent
 */
data class UserLink(val user: User, val link: String, val emailSent: Boolean, val expiresAt: java.time.Instant)

/**
 * The signed-in user, with what the UI needs to shape itself.
 * @param apiKeys their user API keys for the MCP server, one per application they connected
 */
data class Me(val user: User, val claims: LikaClaims, val provider: AuthProviderType, val apiKeys: List<ApiKey> = emptyList())

/** @param name the application which will use the key, e.g. "Claude Desktop" */
data class UserKeyRequest(var name: String = "")

data class ApiKeyRequest(
    var name: String = "",
    var admin: Boolean = false,
    var domains: Map<String, DomainRole> = emptyMap(),
    /** ISO-8601 instant, null for a key that never expires */
    var expiresAt: java.time.Instant? = null
)

data class ApiKeyUpdate(
    var name: String? = null,
    var domains: Map<String, DomainRole>? = null,
    var expiresAt: java.time.Instant? = null,
    var removeExpiry: Boolean = false
)

/** The secret is only ever returned here */
data class ApiKeySecret(val key: ApiKey, val secret: String)
