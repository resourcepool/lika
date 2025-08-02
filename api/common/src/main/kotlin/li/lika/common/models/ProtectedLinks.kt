package li.lika.common.models

object ProtectedLinks {
    val CONTACT = "contact"
    val GTCS = "gtcs"
    val CGV = "cgv"

    fun isProtected(slug: String): Boolean {
        return slug == CONTACT || slug == CGV || slug == GTCS
    }
}
