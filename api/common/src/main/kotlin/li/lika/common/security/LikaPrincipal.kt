package li.lika.common.security

import li.lika.common.models.LikaClaims

/**
 * Who is calling the API: a signed-in user or an application with an API key.
 */
sealed class LikaPrincipal {
    abstract val id: String
    abstract val name: String
    abstract val claims: LikaClaims

    val admin: Boolean
        get() = claims.admin

    /**
     * @param mcpOnly signed in with their user API key: only the MCP server accepts it
     */
    data class UserPrincipal(
        override val id: String,
        val email: String,
        override val name: String,
        override val claims: LikaClaims,
        val mcpOnly: Boolean = false
    ) : LikaPrincipal()

    data class KeyPrincipal(override val id: String, override val name: String, override val claims: LikaClaims) : LikaPrincipal()
}

/**
 * Turns the credentials of a request (a user token or an API key secret) into a principal.
 */
interface CredentialsResolver {
    /**
     * @throws li.lika.common.exceptions.InvalidCredentialsException when the credentials are unknown, expired or revoked
     */
    fun resolve(credentials: String): LikaPrincipal
}
