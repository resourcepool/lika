package li.lika.webapp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*

class DomainAccessTest : ApiTestSupport() {

    @Test
    fun `a key without grants sees no domain, not even standard ones`() {
        val account = createAccount()
        assertEquals(0, call(account.rw, get("/api/domains"))!!.size())
        createLink(account.rw, standardDomainId(), expectedStatus = 404)
        call(account.rw, get("/api/links").param("domainId", standardDomainId()), 404)
    }

    @Test
    fun `a key manages the links of its granted and owned domains`() {
        val granted = createDomain()
        val account = createAccount(setOf(granted.id))
        val owned = createDomain(ownerId = account.id)

        val domains = call(account.rw, get("/api/domains"))!!.map { it.id }.toSet()
        assertEquals(setOf(granted.id, owned.id), domains)
        createLink(account.rw, granted.id)
        createLink(account.rw, owned.id)

        val all = call(account.rw, get("/api/links"))!!
        assertEquals(2, all["totalItemCount"].asInt())
    }

    @Test
    fun `keys sharing a domain manage each other's links, others cannot see them`() {
        val domain = createDomain()
        val alice = createAccount(setOf(domain.id))
        val bob = createAccount(setOf(domain.id))
        val eve = createAccount()
        val link = createLink(alice.rw, domain.id)!!
        assertEquals(alice.id, link["createdBy"].asText())

        call(eve.rw, get("/api/links/${link.id}"), 404)
        call(eve.rw, delete("/api/links/${link.id}"), 404)
        call(eve.rw, put("/api/links").json(mapOf("id" to link.id, "targetUrl" to "https://evil.test", "domainId" to domain.id)), 404)

        call(bob.rw, put("/api/links").json(mapOf("id" to link.id, "targetUrl" to "https://bob.test", "domainId" to domain.id, "slug" to link["slug"].asText())))
        call(bob.rw, delete("/api/links/${link.id}"))
        call(alice.rw, get("/api/links/${link.id}"), 404)
    }

    @Test
    fun `a read token can search but not write`() {
        val domain = createDomain()
        val account = createAccount(setOf(domain.id))
        val link = createLink(account.rw, domain.id)!!
        call(account.read, get("/api/search/slug/${link["slug"].asText()}").param("domainId", domain.id))
        call(account.read, post("/api/links").json(mapOf("targetUrl" to "https://x.test", "domainId" to domain.id)), 403)
    }

    @Test
    fun `admin lists links across all domains when domainId is omitted`() {
        val d1 = createDomain()
        val d2 = createDomain()
        createLink(ADMIN_TOKEN, d1.id)
        createLink(ADMIN_TOKEN, d2.id)
        val hosts = call(ADMIN_TOKEN, get("/api/links").param("limit", "50"))!!["items"].map { it["host"].asText() }.toSet()
        assertTrue(hosts.containsAll(setOf(d1["host"].asText(), d2["host"].asText())))
    }

    @Test
    fun `editing a link keeps its creation date and checks the new slug`() {
        val domain = createDomain()
        val taken = createLink(ADMIN_TOKEN, domain.id)!!
        val link = createLink(ADMIN_TOKEN, domain.id)!!
        val edited = call(ADMIN_TOKEN, put("/api/links").json(mapOf("id" to link.id, "targetUrl" to "https://new.test", "domainId" to domain.id, "slug" to link["slug"].asText())))!!
        assertEquals(link["createdDateTime"].asText(), edited["createdDateTime"].asText())
        call(ADMIN_TOKEN, put("/api/links").json(mapOf("id" to link.id, "targetUrl" to "https://new.test", "domainId" to domain.id, "slug" to taken["slug"].asText())), 409)
    }
}
