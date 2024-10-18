package li.lika.security.models

import li.lika.common.models.Account
import li.lika.security.models.security.Role
import li.lika.security.models.security.Roles
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

class AccessTokenAuthentication private constructor(private val account: Account, private val accessToken: String, val roles: List<Role>) : Authentication {

    private val authorities: List<GrantedAuthority>
    private var authenticated: Boolean

    override fun getName(): String {
        return account.id!!
    }

    fun getId(): String {
        return account.id!!
    }

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return authorities
    }

    override fun getCredentials(): String {
        return accessToken
    }

    override fun getDetails(): Map<String, Any> {
        return emptyMap()
    }

    override fun getPrincipal(): AccessTokenAuthentication {
        return this
    }

    override fun isAuthenticated(): Boolean {
        return authenticated
    }

    @Throws(IllegalArgumentException::class)
    override fun setAuthenticated(isAuthenticated: Boolean) {
        authenticated = isAuthenticated
    }

    companion object {
        fun ofReadWriteAccessToken(account: Account, accessToken: String): AccessTokenAuthentication {
            val roles: List<Role>
            if (account.global) {
                roles = listOf(Roles.globalRoleAdmin())
            } else {
                roles = listOf(Roles.orgRoleReadWrite(account.id!!))
            }
            return AccessTokenAuthentication(account, accessToken, roles)
        }

        fun ofReadAccessToken(account: Account, accessToken: String): AccessTokenAuthentication {
            val roles: List<Role> = listOf(Roles.globalRoleRead())
            return AccessTokenAuthentication(account, accessToken, roles)
        }
    }

    init {

        authorities = roles.map { SimpleGrantedAuthority(it.toAuthority()) }
        authenticated = true
    }
}
