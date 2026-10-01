package li.lika.webapp

import li.lika.core.services.AccountService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*

class AdminEndpointsTest : ApiTestSupport() {

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var accountService: AccountService

    @Test
    fun `global flag is honored on creation and grants admin access`() {
        val admin = createAccount(global = true)
        assertTrue(admin["global"].asBoolean())
        call(admin.rw, get("/api/admin/accounts"))
        call(createAccount().rw, get("/api/admin/accounts"), 403)
    }

    @Test
    fun `rename an account`() {
        val account = createAccount()
        val renamed = call(ADMIN_TOKEN, patch("/api/admin/accounts/${account.id}").json(mapOf("name" to unique("renamed"))))!!
        assertNotEquals(account["name"].asText(), renamed["name"].asText())
        val other = createAccount()
        call(ADMIN_TOKEN, patch("/api/admin/accounts/${account.id}").json(mapOf("name" to other["name"].asText())), 409)
    }

    @Test
    fun `regenerating a token revokes the previous one immediately`() {
        val account = createAccount()
        call(account.rw, get("/api/domains"))
        val updated = call(ADMIN_TOKEN, post("/api/admin/accounts/${account.id}/regenerate").json(mapOf("token" to "read_write")))!!
        assertNotEquals(account.rw, updated.rw)
        assertEquals(account.read, updated.read)
        call(account.rw, get("/api/domains"), 401)
        call(updated.rw, get("/api/domains"))

        val readUpdated = call(ADMIN_TOKEN, post("/api/admin/accounts/${account.id}/regenerate").json(mapOf("token" to "read")))!!
        assertNotEquals(account.read, readUpdated.read)
    }

    @Test
    fun `grants replace the set of domains of a key`() {
        val d1 = createDomain()
        val d2 = createDomain()
        val account = createAccount(setOf(d1.id))
        val updated = call(ADMIN_TOKEN, put("/api/admin/accounts/${account.id}/domains").json(mapOf("domainIds" to listOf(d2.id))))!!
        assertEquals(listOf(d2.id), updated["domainIds"].map { it.asText() })
        assertEquals(listOf(d2.id), call(account.rw, get("/api/domains"))!!.map { it.id })
        call(ADMIN_TOKEN, put("/api/admin/accounts/${account.id}/domains").json(mapOf("domainIds" to listOf("unknown"))), 404)
    }

    @Test
    fun `the last global account cannot be deleted`() {
        val rootId = call(ADMIN_TOKEN, get("/api/admin/accounts"))!!.first { it.rw == ADMIN_TOKEN }.id
        val others = call(ADMIN_TOKEN, get("/api/admin/accounts"))!!.filter { it["global"].asBoolean() && it.id != rootId }
        others.forEach { call(ADMIN_TOKEN, delete("/api/admin/accounts/${it.id}")) }
        call(ADMIN_TOKEN, delete("/api/admin/accounts/$rootId"), 409)
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
    fun `deleting a domain with links requires force, and removes grants`() {
        val domain = createDomain()
        val account = createAccount(setOf(domain.id))
        val link = createLink(account.rw, domain.id)!!
        val conflict = call(ADMIN_TOKEN, delete("/api/admin/domains/${domain.id}"), 409)!!
        assertEquals("domain-not-empty", conflict["errorCode"].asText())

        call(ADMIN_TOKEN, delete("/api/admin/domains/${domain.id}").param("force", "true"))
        call(ADMIN_TOKEN, get("/api/links/${link.id}"), 404)
        assertEquals(0, call(ADMIN_TOKEN, get("/api/admin/accounts/${account.id}"))!!["domainIds"].size())
    }

    @Test
    fun `accounts created before 0_5_0 are granted the standard domains once`() {
        val account = createAccount()
        jdbc.update("update accounts set grants_version = null where id = ?", account.id)
        accountService.migrateLegacyGrants()
        assertTrue(call(account.rw, get("/api/domains"))!!.map { it.id }.contains(standardDomainId()))

        // Migrated once: revoking the grant afterwards sticks
        call(ADMIN_TOKEN, put("/api/admin/accounts/${account.id}/domains").json(mapOf("domainIds" to emptyList<String>())))
        accountService.migrateLegacyGrants()
        assertEquals(0, call(account.rw, get("/api/domains"))!!.size())
    }
}
