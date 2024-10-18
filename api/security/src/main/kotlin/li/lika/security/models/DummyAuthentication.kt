package li.lika.security.models

import li.lika.security.models.security.Roles
import li.lika.security.models.security.utils.Claims.GLOBAL_ROLE_ANONYMOUS
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.*

class DummyAuthentication private constructor(private val credentials: String?, private val authenticated: Boolean, dummyAuthorities: List<String>? = null) : Authentication {
    private val logger = LoggerFactory.getLogger(DummyAuthentication::class.java)

    private val authorities: LinkedList<GrantedAuthority>

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return authorities
    }

    override fun getCredentials(): String? {
        return credentials
    }

    override fun getDetails(): Map<String, Any> {
        return emptyMap()
    }

    override fun getPrincipal(): Any {
        return this
    }

    override fun isAuthenticated(): Boolean {
        return authenticated
    }

    @Throws(UnsupportedOperationException::class)
    override fun setAuthenticated(isAuthenticated: Boolean) {
        logger.warn("setAuthenticated should not be called")
        throw UnsupportedOperationException()
    }

    override fun getName(): String? {
        return null
    }

    companion object {
        fun invalid(): DummyAuthentication {
            return DummyAuthentication(null, false)
        }

        fun anonymous(): DummyAuthentication {
            return DummyAuthentication(null, false)
        }

        fun of(accessToken: String): DummyAuthentication {
            return DummyAuthentication(accessToken, true,null)
        }

        fun admin(): DummyAuthentication {
            return DummyAuthentication(null, true, listOf(Roles.globalRoleAdmin().toAuthority()))
        }
    }

    init {
        authorities = LinkedList()
        if (authenticated) {
            if (dummyAuthorities == null) {
                authorities.add(SimpleGrantedAuthority(Roles.parse(GLOBAL_ROLE_ANONYMOUS).toAuthority()))
            } else {
                authorities.addAll(dummyAuthorities.map { SimpleGrantedAuthority(it) })
            }
        }
    }
}
