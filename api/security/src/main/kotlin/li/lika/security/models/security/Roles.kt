package li.lika.security.models.security

import li.lika.security.models.security.utils.Claims

object Roles {

    fun globalRoleAdmin(): Role {
        return parse(Claims.GLOBAL_ROLE_ADMIN)
    }

    fun globalRoleRead(): Role {
        return parse(Claims.GLOBAL_ROLE_READ)
    }

    fun orgRoleReadWrite(org: String, domain: String? = "*"): Role {
        return parse(Claims.ORG_ROLE_READ_WRITE, org, domain)
    }

    fun orgRoleReadWriteAnyOrgAnyDomain(): Role {
        return parse(Claims.ORG_ROLE_READ_WRITE, Claims.Scope.ANY, Claims.Scope.ANY)
    }

    fun orgRoleReadAnyOrgAnyDomain(): Role {
        return parse(Claims.ORG_ROLE_READ, Claims.Scope.ANY, Claims.Scope.ANY)
    }

    fun parse(claim: String, orgIdOverride: String? = null, domainIdOverride: String? = null): Role {
        // link:{org}:{domain}:{role}
        val prefixStripped = claim.substringAfter(":")
        var orgId: String? = prefixStripped.substringBefore(":")
        val orgIdStripped = prefixStripped.substringAfter(":")
        var domainId: String? = orgIdStripped.substringBefore(":")
        val domainIdStripped = orgIdStripped.substringAfter(":")
        val access = domainIdStripped
        if (orgId == "{org}") {
            if (orgIdOverride == null) {
                throw IllegalStateException("Cannot parse this claim without overriding organization id")
            }
            orgId = orgIdOverride
        }
        if (domainId == "{domain}") {
            if (domainIdOverride == null) {
                throw IllegalStateException("Cannot parse this claim without overriding domain id")
            }
        }
        return Role(orgId, domainId, access)
    }

    fun distinct(vararg authorities: String): Array<String> {
        return authorities.toSet().toTypedArray()
    }

}
