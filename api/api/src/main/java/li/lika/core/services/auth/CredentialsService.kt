package li.lika.core.services.auth

import li.lika.common.exceptions.InvalidCredentialsException
import li.lika.common.exceptions.TokenStaleException
import li.lika.common.exceptions.UserDisabledException
import li.lika.common.models.UserStatus
import li.lika.common.security.CredentialsResolver
import li.lika.common.security.LikaPrincipal
import org.springframework.stereotype.Service

/**
 * Turns the bearer of a request into a principal. User tokens are JWTs; anything else is an API key secret.
 */
@Service
class CredentialsService(
    private val provider: IdentityProvider,
    private val users: UserService,
    private val sessions: SessionService,
    private val keys: ApiKeyService
) : CredentialsResolver {

    override fun resolve(credentials: String): LikaPrincipal {
        return if (credentials.count { it == '.' } == 2) resolveUser(credentials) else resolveKey(credentials)
    }

    private fun resolveUser(token: String): LikaPrincipal {
        val verified = provider.verify(token)
        val user = users.resolve(verified)
        try {
            sessions.assertMayUse(user, verified)
        } catch (e: UserDisabledException) {
            throw InvalidCredentialsException("This user cannot sign in")
        }
        // Rights changed, user disabled then enabled, or sessions revoked since the token was issued.
        // Rights always come from Lika; a token without the claim yet is accepted.
        if (verified.claims != null && verified.claims!!.v != user.version) {
            throw TokenStaleException()
        }
        return LikaPrincipal.UserPrincipal(user.id, user.email, user.name, user.claims())
    }

    private fun resolveKey(secret: String): LikaPrincipal {
        val key = keys.findBySecret(secret) ?: throw InvalidCredentialsException("Unknown API key")
        if (key.isExpired()) {
            throw InvalidCredentialsException("This API key has expired")
        }
        if (key.userId != null) {
            // User API key: the current rights of its user, for the MCP server only
            val user = users.findCached(key.userId!!)?.takeIf { it.status == UserStatus.ACTIVE }
                ?: throw InvalidCredentialsException("The user of this key cannot sign in")
            keys.touch(key)
            return LikaPrincipal.UserPrincipal(user.id, user.email, user.name, user.claims(), mcpOnly = true)
        }
        keys.touch(key)
        return LikaPrincipal.KeyPrincipal(key.id, key.name, key.claims())
    }
}
