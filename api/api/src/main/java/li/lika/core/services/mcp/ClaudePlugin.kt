package li.lika.core.services.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import li.lika.common.security.LikaPrincipal
import li.lika.core.props.McpProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A Claude plugin for Lika: the skills of the MCP server as files, which Claude reads from plugins only,
 * and the MCP server as its connector. Skills follow the rights of the user when it is generated.
 */
@Component
class ClaudePlugin(
    private val skills: McpSkills,
    private val mcp: McpProperties,
    private val mapper: ObjectMapper,
    private val build: ObjectProvider<BuildProperties>
) {
    companion object {
        const val NAME = "lika"
        const val FILE_NAME = "lika-claude-plugin.zip"
        /** Environment variable holding the user API key, for Claude Code */
        const val KEY_VARIABLE = "LIKA_API_KEY"
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC)
    }

    /**
     * @param fallbackMcpUrl the MCP URL the web interface shows, used when LIKA_PUBLIC_API_URL is not set
     */
    fun zip(caller: LikaPrincipal, fallbackMcpUrl: String?): ByteArray {
        val url = mcp.url ?: fallbackMcpUrl?.let(::checkUrl)
            ?: throw IllegalArgumentException("The MCP URL is unknown: set LIKA_PUBLIC_API_URL on the API")
        val files = files(caller, url)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((path, content) in files) {
                zip.putNextEntry(ZipEntry("$NAME/$path"))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Path in the plugin folder → content */
    fun files(caller: LikaPrincipal, mcpUrl: String): Map<String, String> = buildMap {
        val now = Instant.now()
        // A new version each time: the skills depend on the rights of the moment
        val version = (build.ifAvailable?.version ?: "0.0.0").substringBefore("-SNAPSHOT") + "+" + STAMP.format(now)
        put(".claude-plugin/plugin.json", json(linkedMapOf(
            "name" to NAME,
            "displayName" to "Lika",
            "version" to version,
            "description" to "Create, find and manage short links on Lika: links, people, API keys and domains, depending on your role.",
            "author" to mapOf("name" to "Lika", "url" to "https://gitlab.com/resourcepool/lika"),
            "license" to "AGPL-3.0"
        )))
        put(".mcp.json", json(mapOf("mcpServers" to mapOf(NAME to linkedMapOf(
            "type" to "http",
            "url" to mcpUrl,
            // Claude Code reads the key from the environment; no secret is written in the plugin
            "headers" to mapOf("X-Api-Key" to "\${$KEY_VARIABLE}")
        )))))
        put("README.md", readme(caller, mcpUrl))
        for (skill in skills.forCaller(caller)) {
            put("skills/${skill.name}/SKILL.md", skill.markdown)
        }
    }

    private fun readme(caller: LikaPrincipal, mcpUrl: String) = """
        # Lika

        Short links on your own domains, managed from Claude. This plugin brings the skills that teach Claude how Lika
        works: creating, finding, scheduling and protecting links, and, depending on your role, inviting people,
        managing API keys and domains. Its connector is the Lika MCP server at $mcpUrl.

        The skills were generated for ${caller.name} on ${DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.now())},
        with the rights of that moment. Download the plugin again from your Lika profile when your rights change.

        ## Connect

        - **Claude Code**: set `$KEY_VARIABLE` to your user API key (Lika profile, AI assistants), then install the plugin.
        - **Claude Desktop**: keep the MCP server configured as shown in your Lika profile; the plugin adds the skills.

        ## Data

        Claude sends your requests to your Lika server only. The plugin holds no secret.
    """.trimIndent() + "\n"

    private fun json(value: Any) = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n"

    private fun checkUrl(url: String): String {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid MCP URL: $url")
        }
        require((uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()) { "Invalid MCP URL: $url" }
        return url
    }
}
