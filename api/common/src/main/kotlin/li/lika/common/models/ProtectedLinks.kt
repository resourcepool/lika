package li.lika.common.models

object ProtectedLinks {
    val CONTACT = "contact"
    val GTCS = "gtcs"
    val CGV = "cgv"
    val API = "api"
    val STATIC = "static"
    val PUBLIC = "public"

    fun isProtected(slug: String): Boolean {
        val slug = slug.trim().lowercase()
        return slug == CONTACT || slug == CGV || slug == GTCS
    }

    fun isForbidden(slug: String): Boolean {
        val slug = slug.trim().lowercase()
        return slug == API || slug == STATIC || slug == PUBLIC
    }
}
