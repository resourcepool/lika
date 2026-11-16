package li.lika.webapp

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class McpTest : ApiTestSupport() {

    private var nextId = 1

    private fun rpc(token: String?, method: String, params: Map<String, Any?> = emptyMap(), expectedStatus: Int = 200, origin: String? = null): JsonNode? {
        val req = post("/mcp").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .content(mapper.writeValueAsString(mapOf("jsonrpc" to "2.0", "id" to nextId++, "method" to method, "params" to params)))
        token?.let { req.auth(it) }
        origin?.let { req.header("Origin", it) }
        val body = mvc.perform(req).andExpect(status().`is`(expectedStatus)).andReturn().response.contentAsString
        return if (body.isBlank()) null else mapper.readTree(body)
    }

    private fun tools(token: String): Set<String> = rpc(token, "tools/list")!!["result"]["tools"].map { it["name"].asText() }.toSet()

    private fun callTool(token: String, name: String, arguments: Map<String, Any?> = emptyMap()): JsonNode =
        rpc(token, "tools/call", mapOf("name" to name, "arguments" to arguments))!!["result"]

    /** A user's own user API key */
    private fun userKey(session: JsonNode): String = call(session.access, post("/api/me/api-key"))!!.secret

    @Test
    fun `MCP clients must authenticate`() {
        val result = mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"""))
            .andExpect(status().isUnauthorized)
            .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")))
            .andReturn()
        assertEquals(-32001, mapper.readTree(result.response.contentAsString)["error"]["code"].asInt())
        rpc("not-a-key", "tools/list", expectedStatus = 401)
    }

    @Test
    fun `MCP clients may send their key as X-Api-Key`() {
        val key = userKey(createUser(admin = true))
        val body = mvc.perform(post("/mcp").header("X-Api-Key", key).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
            .content("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(mapper.readTree(body)["result"]["tools"].any { it["name"].asText() == "create_domain" })
        mvc.perform(post("/mcp").header("X-Api-Key", "not-a-key").contentType(MediaType.APPLICATION_JSON)
            .content("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `initialize negotiates the protocol, notifications are accepted`() {
        val init = rpc(ADMIN_TOKEN, "initialize", mapOf("protocolVersion" to "2025-06-18", "capabilities" to emptyMap<String, Any>(), "clientInfo" to mapOf("name" to "test", "version" to "1")))!!["result"]
        assertEquals("2025-06-18", init["protocolVersion"].asText())
        assertEquals("lika", init["serverInfo"]["name"].asText())
        assertTrue(init["capabilities"].has("tools"))
        assertTrue(init["instructions"].asText().contains("list_domains"))
        assertEquals("2025-11-25", rpc(ADMIN_TOKEN, "initialize", mapOf("protocolVersion" to "1999-01-01"))!!["result"]["protocolVersion"].asText())

        mvc.perform(post("/mcp").auth(ADMIN_TOKEN).contentType(MediaType.APPLICATION_JSON).content("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
            .andExpect(status().isAccepted)
        assertEquals(-32601, rpc(ADMIN_TOKEN, "nope")!!["error"]["code"].asInt())
        mvc.perform(get("/mcp").auth(ADMIN_TOKEN)).andExpect(status().isMethodNotAllowed)
    }

    @Test
    fun `tools match the rights of the caller`() {
        val domain = createDomain()
        val viewer = tools(createKey(mapOf(domain.id to "viewer")).secret)
        assertTrue(viewer.containsAll(setOf("list_domains", "search_links", "get_link", "list_skills", "get_skill")))
        assertFalse("create_link" in viewer)

        val editorKey = tools(createKey(mapOf(domain.id to "editor")).secret)
        assertTrue("create_link" in editorKey)
        assertFalse("list_users" in editorKey, "application keys never manage people")

        val manager = tools(userKey(createUser(mapOf(domain.id to "manager"))))
        assertTrue(manager.containsAll(setOf("create_link", "list_users", "invite_user", "create_api_key")))
        assertFalse("create_domain" in manager)
        assertFalse("disable_user" in manager)

        val admin = tools(userKey(createUser(admin = true)))
        assertTrue(admin.containsAll(setOf("create_domain", "delete_domain", "disable_user", "export_archive")))
    }

    @Test
    fun `tools act with the caller's rights, and report errors as tool results`() {
        val domain = createDomain()
        val key = createKey(mapOf(domain.id to "editor")).secret

        val created = callTool(key, "create_link", mapOf("domainId" to domain.id, "targetUrl" to "https://example.com/mcp", "slug" to "from-mcp",
            "expiryDate" to "2099-12-31", "metadata" to mapOf("campaign" to "mcp")))
        assertFalse(created["isError"].asBoolean())
        val link = created["structuredContent"]
        assertEquals("https://${domain["host"].asText()}/from-mcp", link["shortenedUrl"].asText())
        assertTrue(created["content"][0]["text"].asText().contains("from-mcp"))

        val updated = callTool(key, "update_link", mapOf("id" to link.id, "targetUrl" to "https://example.com/changed"))["structuredContent"]
        assertEquals("https://example.com/changed", updated["targetUrl"].asText())
        assertEquals("2099-12-31", updated["options"]["expiry"]["expiryDate"].asText(), "fields not given are kept")
        assertEquals("mcp", updated["metadata"]["campaign"].asText())

        val found = callTool(key, "search_links", mapOf("domainId" to domain.id, "query" to "from-mcp"))["structuredContent"]
        assertEquals(1, found["totalItemCount"].asInt())

        val taken = callTool(key, "create_link", mapOf("domainId" to domain.id, "targetUrl" to "https://x.test", "slug" to "from-mcp"))
        assertTrue(taken["isError"].asBoolean())
        assertTrue(taken["content"][0]["text"].asText().contains("slug-already-exists") || taken["content"][0]["text"].asText().contains("already"))

        val elsewhere = callTool(key, "create_link", mapOf("domainId" to createDomain().id, "targetUrl" to "https://x.test"))
        assertTrue(elsewhere["isError"].asBoolean())
        assertTrue(callTool(key, "create_link", mapOf("domainId" to domain.id))["isError"].asBoolean(), "targetUrl is required")

        assertEquals(-32602, rpc(key, "tools/call", mapOf("name" to "create_domain", "arguments" to emptyMap<String, Any>()))!!["error"]["code"].asInt())

        callTool(key, "delete_link", mapOf("id" to link.id))
        call(ADMIN_TOKEN, get("/api/links/${link.id}"), 404)
    }

    @Test
    fun `a manager invites and grants through MCP, on their domains only`() {
        val managed = createDomain()
        val other = createDomain()
        val key = userKey(createUser(mapOf(managed.id to "manager")))
        val invited = callTool(key, "invite_user", mapOf("email" to "${unique("mcp")}@lika.test", "domains" to mapOf(managed.id to "editor")))
        assertFalse(invited["isError"].asBoolean())
        assertTrue(invited["structuredContent"]["link"].asText().contains("/invite/"))
        assertTrue(callTool(key, "invite_user", mapOf("email" to "${unique("mcp")}@lika.test", "domains" to mapOf(other.id to "editor")))["isError"].asBoolean())

        val appKey = callTool(key, "create_api_key", mapOf("name" to "site", "domains" to mapOf(managed.id to "editor")))["structuredContent"]
        assertTrue(appKey["secret"].asText().startsWith("lika_"))
    }

    @Test
    fun `skills are resources tailored to the caller`() {
        val domain = createDomain()
        val editor = userKey(createUser(mapOf(domain.id to "editor")))
        val resources = rpc(editor, "resources/list")!!["result"]["resources"].map { it["uri"].asText() }
        assertEquals(listOf("skill://lika-links/SKILL.md", "skill://lika-rest-api/SKILL.md"), resources)

        val content = rpc(editor, "resources/read", mapOf("uri" to "skill://lika-links/SKILL.md"))!!["result"]["contents"][0]
        assertEquals("text/markdown", content["mimeType"].asText())
        val markdown = content["text"].asText()
        assertTrue(markdown.startsWith("---\nname: lika-links\ndescription: \"Create, find"))
        assertTrue(markdown.contains("\n# Short links on Lika\n"), "not indented")
        assertTrue(markdown.contains("`${domain["host"].asText()}` (id `${domain.id}`): **editor**"))
        assertEquals(-32602, rpc(editor, "resources/read", mapOf("uri" to "skill://lika-admin/SKILL.md"))!!["error"]["code"].asInt())

        val admin = rpc(ADMIN_TOKEN, "resources/list")!!["result"]["resources"].map { it["name"].asText() }
        assertEquals(listOf("lika-links", "lika-access", "lika-admin", "lika-rest-api"), admin)
        assertTrue(callTool(ADMIN_TOKEN, "get_skill", mapOf("name" to "lika-admin"))["content"][0]["text"].asText().contains("# Administering Lika"))
    }

    @Test
    fun `skills follow the Skills over MCP extension`() {
        val init = rpc(ADMIN_TOKEN, "initialize", mapOf("protocolVersion" to "2025-11-25"))!!["result"]
        assertTrue(init["capabilities"]["extensions"].has("io.modelcontextprotocol/skills"))
        val discover = rpc(ADMIN_TOKEN, "server/discover")!!["result"]
        assertTrue("2026-07-28" in discover["supportedVersions"].map { it.asText() })
        assertTrue(discover["capabilities"]["extensions"].has("io.modelcontextprotocol/skills"))

        val list = rpc(ADMIN_TOKEN, "skills/list")!!["result"]
        assertEquals("complete", list["resultType"].asText())
        val admin = list["skills"].first { it["frontmatter"]["name"].asText() == "lika-admin" }
        assertEquals("skill://lika-admin/SKILL.md", admin["uri"].asText())
        assertTrue(admin["frontmatter"]["description"].asText().startsWith("Administer Lika: domains"))

        // The manifest matches the bytes served, and the frontmatter parses back to the entry's
        val text = rpc(ADMIN_TOKEN, "resources/read", mapOf("uri" to admin["uri"].asText()))!!["result"]["contents"][0]["text"].asText()
        val bytes = text.toByteArray(Charsets.UTF_8)
        val file = admin["resources"].single()
        assertEquals(bytes.size, file["size"].asInt())
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("sha256:$sha", file["digest"].asText())
        val yaml = org.yaml.snakeyaml.Yaml().load<Map<String, Any>>(text.removePrefix("---\n").substringBefore("\n---\n"))
        assertEquals(mapper.convertValue(admin["frontmatter"], Map::class.java), yaml)

        assertEquals(admin, rpc(ADMIN_TOKEN, "skills/get", mapOf("uri" to "skill://lika-admin/SKILL.md"))!!["result"]["skill"])
        val viewer = createKey(mapOf(createDomain().id to "viewer")).secret
        assertEquals(-32602, rpc(viewer, "skills/get", mapOf("uri" to "skill://lika-admin/SKILL.md"))!!["error"]["code"].asInt())
    }

    @Test
    fun `a user API key is unique, follows its user, and only works on MCP`() {
        val domain = createDomain()
        val session = createUser(mapOf(domain.id to "viewer"))
        val first = userKey(session)
        assertTrue(first.startsWith("lika_u_"))
        assertEquals(first.take(11), call(session.access, get("/api/me"))!!["apiKey"]["prefix"].asText())

        call(first, get("/api/domains"), 403)
        call(first, get("/api/me"), 403)
        assertFalse("create_link" in tools(first))

        // Rights follow the user immediately
        call(adminSession().access, put("/api/users/${session.userId}/access").json(mapOf("domains" to mapOf(domain.id to "editor"))))
        assertTrue("create_link" in tools(first))

        // Unique: a new one replaces it (with a new session token: the rights changed)
        val second = userKey(login(session.email))
        rpc(first, "tools/list", expectedStatus = 401)
        assertFalse(call(ADMIN_TOKEN, get("/api/keys"))!!.any { it["userId"]?.asText() == session.userId }, "not listed with application keys")

        call(adminSession().access, post("/api/users/${session.userId}/disable"))
        rpc(second, "tools/list", expectedStatus = 401)
        call(adminSession().access, post("/api/users/${session.userId}/enable"))
        tools(second)

        call(adminSession().access, delete("/api/users/${session.userId}/api-key"), 204)
        rpc(second, "tools/list", expectedStatus = 401)
        // Its rights changed since: its first token is stale
        assertTrue(call(login(session.email).access, get("/api/me"))!!["apiKey"].isNull)
    }

    @Test
    fun `browser origins must be allowed`() {
        rpc(ADMIN_TOKEN, "ping", origin = "https://evil.example", expectedStatus = 403)
        rpc(ADMIN_TOKEN, "ping")
    }
}
