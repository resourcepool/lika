package li.lika.webapp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*

class AdminEndpointsTest : ApiTestSupport() {

    @Test
    fun `admin endpoints need an admin`() {
        val domain = createDomain()
        call(createKey(mapOf(domain.id to "editor")).secret, get("/api/admin/domains"), 403)
        call(createUser(mapOf(domain.id to "manager")).access, get("/api/admin/domains"), 403)
        call(createUser(admin = true).access, get("/api/admin/domains"))
        anonymous(get("/api/admin/domains"), 403)
    }

    @Test
    fun `updating a domain keeps its host and rewrites short URLs`() {
        val domain = createDomain()
        val link = createLink(ADMIN_TOKEN, domain.id)!!
        val updated = call(ADMIN_TOKEN, patch("/api/admin/domains/${domain.id}").json(mapOf("apiBaseUrl" to "https://short.test")))!!
        assertEquals(domain["host"].asText(), updated["host"].asText())
        assertEquals("https://short.test", updated["apiBaseUrl"].asText())
        assertEquals("https://short.test/${link["slug"].asText()}", call(ADMIN_TOKEN, get("/api/links/${link.id}"))!!["shortenedUrl"].asText())
    }

    @Test
    fun `deleting a domain with links requires force`() {
        val domain = createDomain()
        val link = createLink(ADMIN_TOKEN, domain.id)!!
        val conflict = call(ADMIN_TOKEN, delete("/api/admin/domains/${domain.id}"), 409)!!
        assertEquals("domain-not-empty", conflict["errorCode"].asText())

        call(ADMIN_TOKEN, delete("/api/admin/domains/${domain.id}").param("force", "true"))
        call(ADMIN_TOKEN, get("/api/links/${link.id}"), 404)
    }
}
