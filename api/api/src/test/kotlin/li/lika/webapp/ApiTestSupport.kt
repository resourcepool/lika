package li.lika.webapp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Runs against the in-memory database seeded from application-dev.yml.
 * Tests share the context: each one creates its own users, keys and domains with unique names.
 */
@SpringBootTest(properties = ["lika.auth.setup-token=${ApiTestSupport.SETUP_TOKEN}", "lika.host-header=${ApiTestSupport.CDN_HOST_HEADER}"])
@ActiveProfiles("dev")
@AutoConfigureMockMvc
abstract class ApiTestSupport {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var mapper: ObjectMapper

    companion object {
        /** Root key of the configuration, migrated to an admin API key */
        const val ADMIN_TOKEN = "lika-client-secret"
        const val STANDARD_HOST = "localhost:8080"
        const val SETUP_TOKEN = "test-setup-token"
        /** Header a CDN sets with the host it received (LIKA_HOST_HEADER) */
        const val CDN_HOST_HEADER = "X-Lika-Host"
        const val ADMIN_EMAIL = "admin@lika.test"
        const val PASSWORD = "correct horse battery"
    }

    fun unique(prefix: String) = "$prefix-${UUID.randomUUID().toString().take(8)}"

    fun MockHttpServletRequestBuilder.auth(token: String) = header("Authorization", "Bearer $token")

    fun MockHttpServletRequestBuilder.json(body: Any) = contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))

    fun ResultActions.body(): JsonNode = mapper.readTree(andReturn().response.contentAsString)

    fun call(token: String?, request: MockHttpServletRequestBuilder, expectedStatus: Int = 200): JsonNode? {
        val req = if (token == null) request else request.auth(token)
        val result = mvc.perform(req.accept(MediaType.APPLICATION_JSON)).andExpect(status().`is`(expectedStatus)).andReturn()
        val content = result.response.contentAsString
        return if (content.isBlank()) null else mapper.readTree(content)
    }

    fun anonymous(request: MockHttpServletRequestBuilder, expectedStatus: Int = 200) = call(null, request, expectedStatus)

    fun standardDomainId(): String {
        return call(ADMIN_TOKEN, get("/api/admin/domains"))!!.first { it["host"].asText() == STANDARD_HOST }["id"].asText()
    }

    fun createDomain(): JsonNode {
        val host = "${unique("d")}.test"
        return call(ADMIN_TOKEN, post("/api/admin/domains").json(mapOf("host" to host, "apiBaseUrl" to "https://$host", "landingBaseUrl" to "")))!!
    }

    /** @return the created key, with its secret */
    fun createKey(domains: Map<String, String> = emptyMap(), admin: Boolean = false, token: String = ADMIN_TOKEN, expiresAt: String? = null): JsonNode {
        return call(token, post("/api/keys").json(mapOf("name" to unique("app"), "admin" to admin, "domains" to domains, "expiresAt" to expiresAt)))!!
    }

    fun createLink(token: String, domainId: String, options: Map<String, Any?> = emptyMap(), slug: String = unique("s"), expectedStatus: Int = 200): JsonNode? {
        return call(token, post("/api/links").json(mapOf("targetUrl" to "https://example.com/$slug", "domainId" to domainId, "slug" to slug, "options" to options)), expectedStatus)
    }

    /** Session of the first admin, created through the setup the first time */
    fun adminSession(): JsonNode {
        val config = anonymous(get("/api/auth/config"))!!
        if (config["setupRequired"].asBoolean()) {
            return anonymous(post("/api/auth/setup").json(mapOf("setupToken" to SETUP_TOKEN, "email" to ADMIN_EMAIL, "name" to "Admin", "password" to PASSWORD)))!!
        }
        return login(ADMIN_EMAIL)
    }

    fun login(email: String, password: String = PASSWORD, expectedStatus: Int = 200): JsonNode =
        anonymous(post("/api/auth/login").json(mapOf("email" to email, "password" to password)), expectedStatus)!!

    fun invite(token: String, domains: Map<String, String> = emptyMap(), admin: Boolean = false, expectedStatus: Int = 200): JsonNode? {
        val email = "${unique("u")}@lika.test"
        return call(token, post("/api/users/invitations").json(mapOf("email" to email, "name" to "User $email", "admin" to admin, "domains" to domains)), expectedStatus)
    }

    /** Invites a user and accepts the invitation: returns their session */
    fun createUser(domains: Map<String, String> = emptyMap(), admin: Boolean = false): JsonNode {
        val invitation = invite(adminSession().access, domains, admin)!!
        return anonymous(post("/api/auth/invitations/${invitation.linkToken}/accept").json(mapOf("password" to PASSWORD)))!!
    }

    val JsonNode.id: String get() = this["id"].asText()
    val JsonNode.secret: String get() = this["secret"].asText()
    val JsonNode.keyId: String get() = this["key"]["id"].asText()
    val JsonNode.access: String get() = this["accessToken"].asText()
    val JsonNode.refresh: String get() = this["refreshToken"].asText()
    val JsonNode.userId: String get() = this["user"]["id"].asText()
    val JsonNode.email: String get() = this["user"]["email"].asText()
    val JsonNode.linkToken: String get() = this["link"].asText().substringAfterLast("/")
}
