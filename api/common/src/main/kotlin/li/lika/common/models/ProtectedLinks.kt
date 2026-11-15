package li.lika.common.models

object ProtectedLinks {
    /** Paths served by the API itself on every domain: never usable as slugs */
    private val RESERVED = setOf("api", "static", "public", "mcp", "actuator", "swagger-ui", "swagger-ui.html", "v3")

    fun isForbidden(slug: String): Boolean = slug.trim().lowercase() in RESERVED
}
