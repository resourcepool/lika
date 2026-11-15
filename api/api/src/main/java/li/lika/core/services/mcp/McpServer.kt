package li.lika.core.services.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import li.lika.common.exceptions.MessageAndErrorCodeException
import li.lika.common.security.LikaPrincipal
import mu.KotlinLogging
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Service

/**
 * Model Context Protocol server, over stateless Streamable HTTP: each JSON-RPC request is answered with
 * a JSON response, and authenticated like any API request. Tools and skills depend on the caller's rights.
 */
@Service
class McpServer(
    private val tools: McpTools,
    private val skills: McpSkills,
    private val mapper: ObjectMapper,
    private val build: ObjectProvider<BuildProperties>
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        /** Newest first */
        val PROTOCOL_VERSIONS = listOf("2025-11-25", "2025-06-18", "2025-03-26")

        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603

        private const val INSTRUCTIONS = """Lika manages short links: domains serve links, each mapping a slug to a target URL.
Your tools match your rights on Lika. Before acting, read the relevant skill (resources lika://skills/..., or list_skills and get_skill):
lika-links for links, lika-access for people and API keys, lika-admin for administration.
Start with list_domains to get domain ids. Confirm with the user before deleting anything or changing a slug that may already be shared."""
    }

    /**
     * @return the response, or null for a notification
     */
    fun handle(caller: LikaPrincipal, message: JsonNode): JsonNode? {
        if (!message.isObject || message.path("jsonrpc").asText() != "2.0" || !message.path("method").isTextual) {
            return error(message.path("id").takeIf { !it.isMissingNode }, INVALID_REQUEST, "Invalid JSON-RPC request")
        }
        val id = message.get("id")
        val method = message.get("method").asText()
        if (id == null) {
            // Notifications (initialized, cancelled...): nothing to do for a stateless server
            return null
        }
        val params = message.path("params")
        return try {
            result(id, dispatch(caller, method, params))
        } catch (e: JsonRpcException) {
            error(id, e.code, e.message)
        } catch (e: Exception) {
            logger.error("MCP {} failed", method, e)
            error(id, INTERNAL_ERROR, "Internal error")
        }
    }

    private fun dispatch(caller: LikaPrincipal, method: String, params: JsonNode): Any = when (method) {
        "initialize" -> initialize(params)
        "ping" -> emptyMap<String, Any>()
        "tools/list" -> mapOf("tools" to tools.visibleTo(caller).map { it.definition() })
        "tools/call" -> callTool(caller, params)
        "resources/list" -> mapOf("resources" to skills.forCaller(caller).map {
            mapOf("uri" to it.uri, "name" to it.name, "title" to it.name, "description" to it.description, "mimeType" to "text/markdown")
        })
        "resources/templates/list" -> mapOf("resourceTemplates" to emptyList<Any>())
        "resources/read" -> readResource(caller, params)
        "prompts/list" -> mapOf("prompts" to emptyList<Any>())
        else -> throw JsonRpcException(METHOD_NOT_FOUND, "Method not found: $method")
    }

    private fun initialize(params: JsonNode): Map<String, Any> {
        val requested = params.path("protocolVersion").asText()
        return mapOf(
            "protocolVersion" to (requested.takeIf { it in PROTOCOL_VERSIONS } ?: PROTOCOL_VERSIONS.first()),
            "capabilities" to mapOf("tools" to mapOf("listChanged" to false), "resources" to mapOf("listChanged" to false, "subscribe" to false)),
            "serverInfo" to mapOf("name" to "lika", "title" to "Lika short links", "version" to (build.ifAvailable?.version ?: "dev")),
            "instructions" to INSTRUCTIONS
        )
    }

    private fun readResource(caller: LikaPrincipal, params: JsonNode): Map<String, Any> {
        val uri = params.path("uri").asText()
        val skill = skills.forCaller(caller).firstOrNull { it.uri == uri } ?: throw JsonRpcException(-32002, "Resource not found: $uri")
        return mapOf("contents" to listOf(mapOf("uri" to skill.uri, "mimeType" to "text/markdown", "text" to skill.markdown)))
    }

    /**
     * Errors of the tool itself (rights, validation, conflicts) are results flagged isError, so the model can read and adapt.
     */
    private fun callTool(caller: LikaPrincipal, params: JsonNode): Map<String, Any> {
        val name = params.path("name").asText()
        val tool = tools.visibleTo(caller).firstOrNull { it.name == name } ?: throw JsonRpcException(INVALID_PARAMS, "Unknown tool: $name")
        val arguments = params.path("arguments").takeIf { it.isObject } ?: mapper.createObjectNode()
        return try {
            val value = tool.call(caller, arguments)
            if (value is String) {
                mapOf("content" to listOf(text(value)), "isError" to false)
            } else {
                val json = mapper.valueToTree<JsonNode>(value ?: emptyMap<String, Any>())
                val structured = if (json.isObject) json else mapper.createObjectNode().set<ObjectNode>("items", json)
                mapOf("content" to listOf(text(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json))), "structuredContent" to structured, "isError" to false)
            }
        } catch (e: MessageAndErrorCodeException) {
            toolError("${e.message} (${e.errorCode})")
        } catch (e: IllegalArgumentException) {
            toolError(e.message ?: "Invalid arguments")
        } catch (e: java.time.format.DateTimeParseException) {
            toolError("Invalid date: ${e.parsedString}")
        }
    }

    private fun text(value: String) = mapOf("type" to "text", "text" to value)

    private fun toolError(message: String) = mapOf("content" to listOf(text(message)), "isError" to true)

    private fun result(id: JsonNode, value: Any): JsonNode =
        mapper.createObjectNode().put("jsonrpc", "2.0").set<ObjectNode>("id", id).set("result", mapper.valueToTree(value))

    fun error(id: JsonNode?, code: Int, message: String): JsonNode {
        val node = mapper.createObjectNode().put("jsonrpc", "2.0")
        node.set<ObjectNode>("id", id ?: mapper.nullNode())
        node.set<ObjectNode>("error", mapper.createObjectNode().put("code", code).put("message", message))
        return node
    }

    private class JsonRpcException(val code: Int, override val message: String) : RuntimeException(message)
}
