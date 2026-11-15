package li.lika.security.models

import li.lika.common.security.LikaPrincipal
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

object Authorities {
    const val ANONYMOUS = "role_anonymous"
    /** Any authenticated caller of the MCP server, user API keys included */
    const val MCP = "role_mcp"
    /** Any authenticated caller of the REST API: user token or application key, not user API keys */
    const val AUTHENTICATED = "role_authenticated"
    const val USER = "role_user"
    const val API_KEY = "role_api_key"
    const val ADMIN = "role_admin"
}

/**
 * A caller authenticated with a user token or an API key. What it may do on each domain is in [LikaPrincipal.claims].
 */
class LikaAuthentication(private val principal: LikaPrincipal, private val credentials: String) : Authentication {

    private val authorities: List<GrantedAuthority> = buildList {
        add(Authorities.MCP)
        if (principal !is LikaPrincipal.UserPrincipal || !principal.mcpOnly) {
            add(Authorities.AUTHENTICATED)
            add(if (principal is LikaPrincipal.UserPrincipal) Authorities.USER else Authorities.API_KEY)
            if (principal.admin) add(Authorities.ADMIN)
        }
    }.map { SimpleGrantedAuthority(it) }

    override fun getName(): String = principal.id
    override fun getAuthorities(): Collection<GrantedAuthority> = authorities
    override fun getCredentials(): String = credentials
    override fun getDetails(): Any? = null
    override fun getPrincipal(): LikaPrincipal = principal
    override fun isAuthenticated(): Boolean = true

    override fun setAuthenticated(isAuthenticated: Boolean) {
        throw IllegalArgumentException("Cannot change the authentication state")
    }
}
