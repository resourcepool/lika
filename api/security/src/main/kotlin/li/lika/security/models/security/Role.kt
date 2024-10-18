package li.lika.security.models.security

import li.lika.security.models.security.utils.Claims

class Role(val organization: String?, val domain: String?, val access: String) {

    fun isOrgGlobal(): Boolean {
        return organization == Claims.Scope.ALL
    }

    fun isOrgAny(): Boolean {
        return organization == Claims.Scope.ANY
    }

    fun isDomainWildcard(): Boolean {
        return domain == Claims.Scope.ALL
    }

    fun isDomainAny(): Boolean {
        return domain == Claims.Scope.ANY
    }

    fun toClaimString(): String {
        return "link:${organization?:'*'}:${domain?:'*'}:${access}"
    }

    fun toAuthority(): String {
        return "role_$access"
    }

    fun supports(role: Role): Boolean {
        return supportsOrganization(role) && supportsDomain(role) && supportsAccess(role)
    }

    private fun supportsAccess(target: Role): Boolean {
        return when(target.access) {
            Claims.AccessLevel.ANONYMOUS -> true
            Claims.AccessLevel.READ -> access != Claims.AccessLevel.ANONYMOUS
            Claims.AccessLevel.READ_WRITE -> access == Claims.AccessLevel.READ_WRITE || access == Claims.AccessLevel.ADMIN
            Claims.AccessLevel.ADMIN -> access == Claims.AccessLevel.ADMIN
            else -> throw IllegalStateException("Wrong access")
        }
    }

    private fun supportsDomain(role: Role): Boolean {
        return isDomainWildcard() || role.isDomainAny() || domain == role.domain
    }

    private fun supportsOrganization(role: Role): Boolean {
        return isOrgGlobal() || role.isOrgAny() || organization == role.organization
    }
}
