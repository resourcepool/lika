package li.lika.webapp

import li.lika.core.services.auth.AccessMigrationService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import java.time.Instant

class ApiKeysTest : ApiTestSupport() {

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var migration: AccessMigrationService

    @Test
    fun `an editor key manages the links of its domains only`() {
        val mine = createDomain()
        val other = createDomain()
        val key = createKey(mapOf(mine.id to "editor"))
        assertTrue(key.secret.startsWith("lika_"))
        assertNull(key["key"]["secretHash"])

        assertEquals(listOf(mine.id), call(key.secret, get("/api/domains"))!!.map { it.id })
        createLink(key.secret, mine.id)
        createLink(key.secret, other.id, expectedStatus = 404)
        call(key.secret, get("/api/links").param("domainId", other.id), 404)
        assertEquals(1, call(key.secret, get("/api/links"))!!["totalItemCount"].asInt())
    }

    @Test
    fun `keys sharing a domain manage each other's links, others cannot see them`() {
        val domain = createDomain()
        val alice = createKey(mapOf(domain.id to "editor"))
        val bob = createKey(mapOf(domain.id to "editor"))
        val eve = createKey(mapOf(createDomain().id to "editor"))
        val link = createLink(alice.secret, domain.id)!!
        assertEquals(alice.keyId, link["createdBy"].asText())

        call(eve.secret, get("/api/links/${link.id}"), 404)
        call(eve.secret, delete("/api/links/${link.id}"), 404)
        call(eve.secret, put("/api/links").json(mapOf("id" to link.id, "targetUrl" to "https://evil.test", "domainId" to domain.id)), 404)

        call(bob.secret, put("/api/links").json(mapOf("id" to link.id, "targetUrl" to "https://bob.test", "domainId" to domain.id, "slug" to link["slug"].asText())))
        call(bob.secret, delete("/api/links/${link.id}"))
        call(alice.secret, get("/api/links/${link.id}"), 404)
    }

    @Test
    fun `a viewer key can search but not write`() {
        val domain = createDomain()
        val link = createLink(ADMIN_TOKEN, domain.id)!!
        val viewer = createKey(mapOf(domain.id to "viewer"))
        call(viewer.secret, get("/api/search/slug/${link["slug"].asText()}").param("domainId", domain.id))
        call(viewer.secret, post("/api/links").json(mapOf("targetUrl" to "https://x.test", "domainId" to domain.id)), 403)
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

    @Test
    fun `regenerating or deleting a key revokes its secret immediately`() {
        val domain = createDomain()
        val key = createKey(mapOf(domain.id to "editor"))
        call(key.secret, get("/api/domains"))
        val regenerated = call(ADMIN_TOKEN, post("/api/keys/${key.keyId}/regenerate"))!!
        call(key.secret, get("/api/domains"), 401)
        call(regenerated.secret, get("/api/domains"))

        call(ADMIN_TOKEN, delete("/api/keys/${key.keyId}"), 204)
        call(regenerated.secret, get("/api/domains"), 401)
    }

    @Test
    fun `expired keys are refused`() {
        val key = createKey(mapOf(createDomain().id to "viewer"), expiresAt = Instant.now().minusSeconds(60).toString())
        call(key.secret, get("/api/domains"), 401)
    }

    @Test
    fun `keys never manage, and an admin key needs an admin`() {
        val domain = createDomain()
        call(ADMIN_TOKEN, post("/api/keys").json(mapOf("name" to "x", "domains" to mapOf(domain.id to "manager"))), 400)
        call(ADMIN_TOKEN, post("/api/keys").json(mapOf("name" to "x", "domains" to emptyMap<String, String>())), 400)
        val adminKey = createKey(admin = true)
        call(adminKey.secret, get("/api/admin/domains"))
    }

    @Test
    fun `applications do not manage users or keys`() {
        val key = createKey(mapOf(createDomain().id to "editor"))
        call(key.secret, get("/api/users"), 403)
        call(key.secret, get("/api/keys"), 403)
        call(key.secret, get("/api/me"), 403)
    }

    @Test
    fun `managers only see and manage the keys of their domains`() {
        val managed = createDomain()
        val other = createDomain()
        val manager = createUser(mapOf(managed.id to "manager"))
        val mine = createKey(mapOf(managed.id to "editor"), token = manager.access)
        call(manager.access, post("/api/keys").json(mapOf("name" to "x", "domains" to mapOf(other.id to "editor"))), 403)
        call(manager.access, post("/api/keys").json(mapOf("name" to "x", "admin" to true)), 403)
        val foreign = createKey(mapOf(managed.id to "editor", other.id to "viewer"))

        val visible = call(manager.access, get("/api/keys"))!!.map { it.id }
        assertTrue(mine.keyId in visible)
        assertFalse(foreign.keyId in visible)
        call(manager.access, delete("/api/keys/${foreign.keyId}"), 404)
        call(manager.access, patch("/api/keys/${mine.keyId}").json(mapOf("domains" to mapOf(other.id to "editor"))), 403)
        call(manager.access, delete("/api/keys/${mine.keyId}"), 204)
    }

    @Test
    fun `accounts of Lika before 0_7_0 become keys keeping their tokens`() {
        val granted = createDomain()
        val owned = createDomain()
        val id = unique("acc")
        val rw = unique("rw").padEnd(64, 'x')
        val ro = unique("ro").padEnd(64, 'y')
        jdbc.update("insert into accounts (id, name, global, default_access_token, default_public_read_access_token) values (?, ?, false, ?, ?)", id, id, rw, ro)
        jdbc.update("insert into account_domains (account_id, domain_id) values (?, ?)", id, granted.id)
        jdbc.update("update domains set owner_id = ? where id = ?", id, owned.id)

        migration.migrate()

        assertEquals(setOf(granted.id, owned.id), call(rw, get("/api/domains"))!!.map { it.id }.toSet())
        createLink(rw, owned.id)
        call(ro, post("/api/links").json(mapOf("targetUrl" to "https://x.test", "domainId" to granted.id)), 403)
        assertEquals(0, jdbc.queryForObject("select count(*) from accounts", Int::class.java))
        val keys = call(ADMIN_TOKEN, get("/api/keys"))!!.map { it["name"].asText() }
        assertTrue(keys.containsAll(listOf(id, "$id (read)")))
    }
}
