package li.lika.core.props

import li.lika.common.models.AuthProviderType
import li.lika.common.models.LikaClaims
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "lika.auth")
class AuthProperties {
    /** The single identity provider users sign in with (LIKA_AUTH_PROVIDER) */
    var provider: AuthProviderType = AuthProviderType.LOCAL
    /** Local provider: secret signing the access tokens, at least 32 characters (LIKA_JWT_SECRET) */
    var jwtSecret: String? = null
    /** Public URL of the web app, used in the links of invitations and password resets (LIKA_WEB_URL) */
    var webUrl: String = "http://localhost:3000"
    /** Fixed setup token, instead of a random one printed in the logs (LIKA_SETUP_TOKEN). For automated installs. */
    var setupToken: String? = null
    /**
     * Name of the claim carrying the rights in the tokens of Keycloak and Firebase, and of the Keycloak user attribute
     * (LIKA_CLAIM_NAME), e.g. `acme_lika`: avoids a clash with a claim `lika` written by another application.
     * Letters, digits, `_` and `-` only. Ignored by the local provider, whose tokens are Lika's own.
     */
    var claimName: String = LikaClaims.CLAIM_NAME
    var keycloak = Keycloak()
    var firebase = Firebase()
    var accessTokenTtl: Duration = Duration.ofMinutes(15)
    var refreshTokenTtl: Duration = Duration.ofDays(30)
    var invitationTtl: Duration = Duration.ofDays(7)
    var passwordResetTtl: Duration = Duration.ofHours(24)
}

/**
 * Keycloak realm users sign in with. The confidential client serves the sign-in (authorization code with PKCE)
 * and, through its service account, the admin API: realm-management roles manage-users, view-users,
 * manage-clients and manage-realm.
 */
class Keycloak {
    /** e.g. https://sso.example.com (LIKA_KEYCLOAK_URL) */
    var url: String? = null
    /** LIKA_KEYCLOAK_REALM */
    var realm: String? = null
    /** LIKA_KEYCLOAK_CLIENT_ID */
    var clientId: String? = null
    /** LIKA_KEYCLOAK_CLIENT_SECRET */
    var clientSecret: String? = null
    /** Label of the sign-in button (LIKA_KEYCLOAK_LABEL) */
    var label: String = "Keycloak"
}

/**
 * Firebase Authentication project users sign in with: email and password, and Google when its OAuth client is set.
 */
class Firebase {
    /** LIKA_FIREBASE_PROJECT_ID */
    var projectId: String? = null
    /** Web API key of the project (LIKA_FIREBASE_API_KEY) */
    var apiKey: String? = null
    /** Service account key file, encoded in base64 (LIKA_FIREBASE_SERVICE_ACCOUNT). Its JSON or its path are also accepted. */
    var serviceAccount: String? = null
    /** host:port of the Auth emulator, for development and tests (FIREBASE_AUTH_EMULATOR_HOST) */
    var emulatorHost: String? = null
    /** OAuth client of Google sign-in, as configured in the Google provider of Firebase (LIKA_FIREBASE_GOOGLE_CLIENT_ID) */
    var googleClientId: String? = null
    /** LIKA_FIREBASE_GOOGLE_CLIENT_SECRET */
    var googleClientSecret: String? = null
}

@ConfigurationProperties(prefix = "lika.mcp")
class McpProperties {
    /** Public URL of this API, as AI assistants reach it, e.g. https://go.example.com (LIKA_PUBLIC_API_URL) */
    var publicUrl: String? = null
    /**
     * Origins of browser-based MCP clients allowed to call /mcp, or `*` (LIKA_MCP_ALLOWED_ORIGINS).
     * Requests without an Origin header (desktop and server clients) are always accepted.
     */
    var allowedOrigins: List<String> = emptyList()

    val url: String?
        get() = publicUrl?.trimEnd('/')?.takeIf { it.isNotBlank() }?.let { "$it/mcp" }
}
