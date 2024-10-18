package li.lika.common.models

object ProtectedLinks {
    val CONTACT = "contact"
    val GTCS = "gtcs"
    val CGV = "cgv"
    val TMP_FREE = "free"

    fun isProtected(slug: String): Boolean {
        return slug == CONTACT || slug == CGV || slug == GTCS || slug == TMP_FREE
    }
}
