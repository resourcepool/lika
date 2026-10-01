package li.lika.webapp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Runs against the in-memory database seeded from application-default.yml.
 * Tests share the context: each one creates its own accounts and domains with unique names.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiTestSupport {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var mapper: ObjectMapper

    companion object {
        const val ADMIN_TOKEN = "lika-client-secret"
        const val STANDARD_HOST = "localhost:8080"
    }

    fun unique(prefix: String) = "$prefix-${UUID.randomUUID().toString().take(8)}"

    fun MockHttpServletRequestBuilder.auth(token: String) = header("Authorization", "Bearer $token")

    fun MockHttpServletRequestBuilder.json(body: Any) = contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))

    fun ResultActions.body(): JsonNode = mapper.readTree(andReturn().response.contentAsString)

    fun call(token: String, request: MockHttpServletRequestBuilder, expectedStatus: Int = 200): JsonNode? {
        val result = mvc.perform(request.auth(token).accept(MediaType.APPLICATION_JSON)).andExpect(status().`is`(expectedStatus)).andReturn()
        val content = result.response.contentAsString
        return if (content.isBlank()) null else mapper.readTree(content)
    }

    fun standardDomainId(): String {
        return call(ADMIN_TOKEN, get("/api/admin/domains"))!!.first { it["host"].asText() == STANDARD_HOST }["id"].asText()
    }

    fun createDomain(ownerId: String? = null): JsonNode {
        val host = "${unique("d")}.test"
        val req = post("/api/admin/domains").json(mapOf("host" to host, "apiBaseUrl" to "https://$host", "landingBaseUrl" to ""))
        ownerId?.let { req.param("ownerId", it) }
        return call(ADMIN_TOKEN, req)!!
    }

    fun createAccount(domainIds: Set<String> = emptySet(), global: Boolean = false): JsonNode {
        return call(ADMIN_TOKEN, post("/api/admin/accounts").json(mapOf("name" to unique("acc"), "global" to global, "domainIds" to domainIds)))!!
    }

    fun createLink(token: String, domainId: String, options: Map<String, Any?> = emptyMap(), slug: String = unique("s"), expectedStatus: Int = 200): JsonNode? {
        return call(token, post("/api/links").json(mapOf("targetUrl" to "https://example.com/$slug", "domainId" to domainId, "slug" to slug, "options" to options)), expectedStatus)
    }

    val JsonNode.rw: String get() = this["defaultAccessToken"].asText()
    val JsonNode.read: String get() = this["defaultPublicReadAccessToken"].asText()
    val JsonNode.id: String get() = this["id"].asText()
}
