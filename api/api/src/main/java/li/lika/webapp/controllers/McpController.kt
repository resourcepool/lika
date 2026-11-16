package li.lika.webapp.controllers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import li.lika.common.security.LikaPrincipal
import li.lika.core.props.McpProperties
import li.lika.core.services.mcp.McpServer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * MCP endpoint (Streamable HTTP, stateless): POST JSON-RPC messages, get JSON responses.
 * Accepts user tokens, application API keys and user API keys, which only work here.
 */
@RequestMapping("/mcp")
@RestController
class McpController(private val server: McpServer, private val props: McpProperties, private val mapper: ObjectMapper) {

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun post(@RequestBody body: String, @RequestHeader(HttpHeaders.ORIGIN, required = false) origin: String?): ResponseEntity<Any> {
        // Browsers send an Origin: only trusted web clients may call (DNS rebinding protection)
        if (origin != null && "*" !in props.allowedOrigins && origin !in props.allowedOrigins) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(server.error(null, -32600, "Origin not allowed: $origin"))
        }
        val caller = SecurityContextHolder.getContext().authentication?.principal as? LikaPrincipal
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"lika\", error=\"invalid_token\", error_description=\"Send a Lika API key: Authorization: Bearer <key>, or X-Api-Key: <key>\"")
                .body(server.error(null, -32001, "Authentication required: send a Lika API key as Authorization: Bearer <key> or X-Api-Key: <key>"))

        val message = try {
            mapper.readTree(body)
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(server.error(null, McpServer.PARSE_ERROR, "Parse error"))
        }
        if (message.isArray) {
            // Batches (protocol 2025-03-26)
            val responses = message.mapNotNull { server.handle(caller, it) }
            return if (responses.isEmpty()) ResponseEntity.accepted().build() else ResponseEntity.ok(responses)
        }
        val response: JsonNode = server.handle(caller, message) ?: return ResponseEntity.accepted().build()
        return ResponseEntity.ok(response)
    }

    /** No server-initiated stream: this server never sends requests or notifications on its own */
    @GetMapping
    fun stream(): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build()

    /** Stateless: no session to end */
    @DeleteMapping
    fun endSession(): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build()
}
