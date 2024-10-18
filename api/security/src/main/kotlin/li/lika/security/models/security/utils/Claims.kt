package li.lika.security.models.security.utils

object Claims {
    // Roles
    const val GLOBAL_ROLE_ANONYMOUS = "link:${Scope.ALL}:${Scope.ALL}:${AccessLevel.ANONYMOUS}"
    const val GLOBAL_ROLE_ADMIN = "link:${Scope.ALL}:${Scope.ALL}:${AccessLevel.ADMIN}"
    const val GLOBAL_ROLE_READ = "link:${Scope.ALL}:${Scope.ALL}:${AccessLevel.READ}"
    const val ORG_ROLE_READ = "link:{org}:{domain}:${AccessLevel.READ}"
    const val ORG_ROLE_READ_WRITE = "link:{org}:{domain}:${AccessLevel.READ_WRITE}"
    const val ORG_ROLE_ADMIN = "link:{org}:{domain}:${AccessLevel.ADMIN}"

    object AccessLevel {
        const val ANONYMOUS = "anonymous"
        const val READ = "read"
        const val READ_WRITE = "read_write"
        const val ADMIN = "admin"
    }

    object Scope {
        const val ALL = "*"
        const val ANY = "?"
    }
}
