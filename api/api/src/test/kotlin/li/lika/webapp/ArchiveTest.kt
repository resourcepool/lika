package li.lika.webapp

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*

class ArchiveTest : ApiTestSupport() {

    private fun export(): JsonNode = call(ADMIN_TOKEN, post("/api/admin/archive/export"))!!

    private fun import(archive: Any, conflict: String = "skip", dryRun: Boolean = false): JsonNode =
        call(ADMIN_TOKEN, post("/api/admin/archive/import").param("conflict", conflict).param("dryRun", dryRun.toString()).json(archive))!!

    @Test
    fun `export v3 keeps users, keys, roles, metadata, passwords and timestamps`() {
        val domain = createDomain()
        val key = createKey(mapOf(domain.id to "editor"))
        val user = createUser(mapOf(domain.id to "manager"))
        val link = call(key.secret, post("/api/links").json(mapOf(
            "targetUrl" to "https://example.com", "domainId" to domain.id, "slug" to "meta",
            "metadata" to mapOf("campaign" to "spring"), "options" to mapOf("password" to mapOf("password" to "pw"))
        )))!!
        val host = domain["host"].asText()

        val archive = export()
        assertEquals(3, archive["version"].asInt())
        assertEquals(0, archive["accounts"].size())
        val exportedUser = archive["users"].first { it["email"].asText() == user.email }
        assertEquals("manager", exportedUser["domains"][host].asText())
        assertTrue(exportedUser["passwordHash"].asText().startsWith("$2"))
        val exportedKey = archive["apiKeys"].first { it["name"].asText() == key["key"]["name"].asText() }
        assertEquals("editor", exportedKey["domains"][host].asText())
        assertEquals(64, exportedKey["secretHash"].asText().length)
        assertFalse(archive.toString().contains(key.secret))

        val exportedLink = archive["domains"].first { it["host"].asText() == host }["links"][0]
        assertEquals("spring", exportedLink["metadata"]["campaign"].asText())
        assertTrue(exportedLink["passwordHash"].asText().startsWith("$2"))
        assertEquals(key["key"]["name"].asText(), exportedLink["createdBy"].asText())
        assertEquals(link["createdDateTime"].asText().take(19), exportedLink["createdDateTime"].asText().take(19))
    }

    @Test
    fun `a v3 archive restores users and keys with their secrets`() {
        val domain = createDomain()
        val key = createKey(mapOf(domain.id to "editor"))
        val user = createUser(mapOf(domain.id to "editor"))
        val archive = export()
        call(ADMIN_TOKEN, delete("/api/keys/${key.keyId}"), 204)
        call(adminSession().access, delete("/api/users/${user.userId}"), 204)

        val report = import(archive)
        assertEquals(1, report["apiKeys"]["created"].asInt())
        assertEquals(1, report["users"]["created"].asInt())
        createLink(key.secret, domain.id)
        createLink(login(user.email).access, domain.id)
    }

    @Test
    fun `dry run reports without writing, then skip and overwrite modes`() {
        val domain = createDomain()
        val link = createLink(ADMIN_TOKEN, domain.id)!!
        val archive = export()
        call(ADMIN_TOKEN, delete("/api/links/${link.id}"))

        val dry = import(archive, dryRun = true)
        assertTrue(dry["dryRun"].asBoolean())
        assertEquals(1, dry["links"]["created"].asInt())
        call(ADMIN_TOKEN, get("/api/links/slug/${link["slug"].asText()}").param("domainId", domain.id))

        val skipped = import(archive)
        assertEquals(1, skipped["links"]["created"].asInt())
        assertEquals(0, skipped["links"]["updated"].asInt())
        call(ADMIN_TOKEN, get("/api/links/slug/${link["slug"].asText()}").param("domainId", domain.id), 409)

        val overwritten = import(archive, conflict = "overwrite")
        assertEquals(0, overwritten["links"]["created"].asInt())
        assertTrue(overwritten["links"]["updated"].asInt() >= 1)
    }

    @Test
    fun `v1 archives still import, their accounts becoming keys with the standard domains`() {
        val name = unique("legacy")
        val host = "${unique("v1")}.test"
        val rw = unique("rw").padEnd(40, 'x')
        val v1 = mapOf("accounts" to listOf(mapOf(
            "name" to name, "root" to false, "global" to false,
            "secretToken" to rw, "publicReadOnlyToken" to unique("ro").padEnd(40, 'x'),
            "domains" to listOf(
                mapOf("standard" to true, "host" to STANDARD_HOST, "apiBaseUrl" to "http://localhost:8080", "links" to emptyList<Any>()),
                mapOf("standard" to false, "host" to host, "apiBaseUrl" to "https://$host", "links" to listOf(
                    mapOf("targetUrl" to "https://example.com", "slug" to "old", "type" to "SIMPLE")
                ))
            )
        )))
        val report = import(v1)
        assertEquals(2, report["apiKeys"]["created"].asInt())
        assertEquals(1, report["domains"]["created"].asInt())
        assertEquals(1, report["links"]["created"].asInt())

        val hosts = call(rw, get("/api/domains"))!!.map { it["host"].asText() }.toSet()
        assertEquals(setOf(STANDARD_HOST, host), hosts)
    }
}
