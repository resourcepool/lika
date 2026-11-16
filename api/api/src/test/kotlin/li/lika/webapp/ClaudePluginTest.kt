package li.lika.webapp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ClaudePluginTest : ApiTestSupport() {

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { put(it.name, zip.readBytes().toString(Charsets.UTF_8)) }
        }
    }

    @Test
    fun `the plugin holds the skills of the user, and the MCP server as connector`() {
        val domain = createDomain()
        val session = createUser(mapOf(domain.id to "manager"))
        val result = mvc.perform(get("/api/me/claude-plugin").param("mcpUrl", "https://go.example.test/mcp").auth(session.access))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.parseMediaType("application/zip")))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"lika-claude-plugin.zip\""))
            .andReturn()
        val files = unzip(result.response.contentAsByteArray)

        assertEquals(
            setOf("lika/.claude-plugin/plugin.json", "lika/.mcp.json", "lika/README.md",
                "lika/skills/lika-links/SKILL.md", "lika/skills/lika-access/SKILL.md", "lika/skills/lika-rest-api/SKILL.md"),
            files.keys
        )
        val manifest = mapper.readTree(files["lika/.claude-plugin/plugin.json"])
        assertEquals("lika", manifest["name"].asText())
        assertTrue(manifest["version"].asText().matches(Regex("""\d+\.\d+\.\d+\+\d{14}""")), manifest["version"].asText())

        val server = mapper.readTree(files["lika/.mcp.json"])["mcpServers"]["lika"]
        assertEquals("https://go.example.test/mcp", server["url"].asText())
        assertEquals("\${LIKA_API_KEY}", server["headers"]["X-Api-Key"].asText(), "no secret in the plugin")

        // The same skills as the MCP server serves the user
        val skill = files["lika/skills/lika-links/SKILL.md"]!!
        assertTrue(skill.startsWith("---\nname: lika-links\n"))
        assertTrue(skill.contains("(id `${domain.id}`): **manager**"))
        assertTrue(files["lika/README.md"]!!.split(Regex("\\s+")).size >= 40)
    }

    @Test
    fun `only users get a plugin, for a valid MCP URL`() {
        val session = createUser(mapOf(createDomain().id to "viewer"))
        call(session.access, get("/api/me/claude-plugin").param("mcpUrl", "javascript:alert(1)"), 400)
        // Without LIKA_PUBLIC_API_URL nor the URL of the web interface, the MCP URL is unknown
        call(session.access, get("/api/me/claude-plugin"), 400)
        val key = createKey(mapOf(createDomain().id to "editor")).secret
        call(key, get("/api/me/claude-plugin").param("mcpUrl", "https://go.example.test/mcp"), 403)
    }
}
