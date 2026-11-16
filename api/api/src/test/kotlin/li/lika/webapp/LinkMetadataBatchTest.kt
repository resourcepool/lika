package li.lika.webapp

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import java.time.LocalDate

class LinkMetadataBatchTest : ApiTestSupport() {

    private fun link(token: String, domainId: String, metadata: Map<String, String>): JsonNode {
        val slug = unique("m")
        return call(token, post("/api/links").json(mapOf("targetUrl" to "https://example.com/$slug", "domainId" to domainId, "slug" to slug, "metadata" to metadata)))!!
    }

    private fun search(token: String, domainId: String?, vararg meta: String, filter: String? = null): List<String> {
        val req = get("/api/links").param("limit", "50")
        domainId?.let { req.param("domainId", it) }
        filter?.let { req.param("filter", it) }
        meta.forEach { req.param("meta", it) }
        return call(token, req)!!["items"].map { it["id"].asText() }
    }

    @Test
    fun `links are filtered on metadata, all filters matching, ignoring case`() {
        val domain = createDomain()
        val summerFr = link(ADMIN_TOKEN, domain.id, mapOf("campaign" to "summer", "lang" to "fr"))
        val summerEn = link(ADMIN_TOKEN, domain.id, mapOf("campaign" to "Summer", "lang" to "en"))
        val winter = link(ADMIN_TOKEN, domain.id, mapOf("campaign" to "winter-sale"))
        val none = link(ADMIN_TOKEN, domain.id, emptyMap())

        assertEquals(setOf(summerFr.id, summerEn.id), search(ADMIN_TOKEN, domain.id, "campaign=summer").toSet())
        assertEquals(listOf(summerFr.id), search(ADMIN_TOKEN, domain.id, "campaign=summer", "lang=fr"))
        // A key alone: any value
        assertEquals(setOf(summerFr.id, summerEn.id, winter.id), search(ADMIN_TOKEN, domain.id, "CAMPAIGN").toSet())
        // Whole values only, wildcards are plain characters
        assertTrue(search(ADMIN_TOKEN, domain.id, "campaign=sum").isEmpty())
        assertTrue(search(ADMIN_TOKEN, domain.id, "campaign=%").isEmpty())
        assertTrue(search(ADMIN_TOKEN, domain.id, "campaign=winter_sale").isEmpty())
        assertEquals(listOf(winter.id), search(ADMIN_TOKEN, domain.id, "campaign=winter-sale"))
        // With the text filter, and across domains
        assertEquals(listOf(summerFr.id), search(ADMIN_TOKEN, domain.id, "lang=fr", filter = summerFr["slug"].asText()))
        assertTrue(summerEn.id in search(ADMIN_TOKEN, null, "lang=en"))
        assertFalse(none.id in search(ADMIN_TOKEN, domain.id, "lang"))

        val values = call(ADMIN_TOKEN, get("/api/links/metadata").param("domainId", domain.id))!!
        val campaign = values.first { it["key"].asText() == "campaign" }
        assertEquals(3, campaign["linkCount"].asInt())
        assertEquals(setOf("summer", "Summer", "winter-sale"), campaign["values"].map { it.asText() }.toSet())
        assertEquals(listOf("campaign", "lang"), values.map { it["key"].asText() })
    }

    @Test
    fun `metadata keys and values with quotes and accents are matched`() {
        val domain = createDomain()
        val odd = link(ADMIN_TOKEN, domain.id, mapOf("quote \"x\"" to "a\\b", "été" to "Ünïcode"))
        assertEquals(listOf(odd.id), search(ADMIN_TOKEN, domain.id, "quote \"x\"=a\\b"))
        assertEquals(listOf(odd.id), search(ADMIN_TOKEN, domain.id, "été=Ünïcode"))
    }

    @Test
    fun `batch operations change every link, or none`() {
        val domain = createDomain()
        val editor = createKey(mapOf(domain.id to "editor")).secret
        val a = link(editor, domain.id, mapOf("team" to "growth", "old" to "1"))
        val b = link(editor, domain.id, mapOf("old" to "2"))
        val ids = listOf(a.id, b.id)

        var result = call(editor, post("/api/links/batch").json(mapOf("ids" to ids, "action" to "set_metadata", "metadata" to mapOf("campaign" to "q3"))))!!
        assertEquals(2, result["count"].asInt())
        assertEquals("growth", result["links"][0]["metadata"]["team"].asText(), "other entries are kept")
        assertEquals(ids.toSet(), search(editor, domain.id, "campaign=q3").toSet())

        result = call(editor, post("/api/links/batch").json(mapOf("ids" to ids, "action" to "remove_metadata", "keys" to listOf("old"))))!!
        assertTrue(result["links"].none { it["metadata"].has("old") })

        val date = LocalDate.now().plusDays(30).toString()
        call(editor, post("/api/links/batch").json(mapOf("ids" to ids, "action" to "set_expiry", "expiryDate" to date)))
        assertEquals(date, call(editor, get("/api/links/${a.id}"))!!["options"]["expiry"]["expiryDate"].asText())
        call(editor, post("/api/links/batch").json(mapOf("ids" to ids, "action" to "set_expiry", "expiryDate" to null)))
        assertTrue(call(editor, get("/api/links/${a.id}"))!!["options"]["expiry"].isNull)

        // A link the caller cannot edit: nothing changes
        val other = createDomain()
        val foreign = link(ADMIN_TOKEN, other.id, emptyMap())
        call(editor, post("/api/links/batch").json(mapOf("ids" to ids + foreign.id, "action" to "delete")), 404)
        call(editor, get("/api/links/${a.id}"))
        val viewer = createKey(mapOf(domain.id to "viewer")).secret
        call(viewer, post("/api/links/batch").json(mapOf("ids" to ids, "action" to "delete")), 403)

        call(editor, post("/api/links/batch").json(mapOf("ids" to ids, "action" to "set_metadata")), 400)
        call(editor, post("/api/links/batch").json(mapOf("ids" to emptyList<String>(), "action" to "delete")), 400)
        call(editor, post("/api/links/batch").json(mapOf("ids" to ids, "action" to "set_expiry", "expiryDate" to "soon")), 400)

        assertEquals(2, call(editor, post("/api/links/batch").json(mapOf("ids" to ids, "action" to "delete")))!!["count"].asInt())
        call(editor, get("/api/links/${a.id}"), 404)
        call(editor, get("/api/links/${b.id}"), 404)
    }
}
