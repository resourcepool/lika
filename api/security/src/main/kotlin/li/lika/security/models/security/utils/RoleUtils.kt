package li.lika.security.models.security.utils

import li.lika.security.models.security.Role

object RoleUtils {

    fun hasRole(targetRole: Role, roles: Collection<Role>): Boolean {
        for (currentRole in roles) {
            if (currentRole.supports(targetRole)) {
                return true
            }
        }
        return false
    }
}
