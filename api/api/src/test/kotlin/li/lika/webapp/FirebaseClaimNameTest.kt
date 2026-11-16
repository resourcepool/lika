package li.lika.webapp

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.web.client.RestClient
import java.util.Base64

/**
 * LIKA_CLAIM_NAME with Firebase, and the custom claims of other applications. Against the Auth emulator,
 * skipped when it is not running.
 */
@EnabledIf("li.lika.webapp.FirebaseProviderTest#emulatorRunning")
class FirebaseClaimNameTest : ApiTestSupport() {

    companion object {
        const val CLAIM = "acme_lika"
        private val host: String = System.getenv("FIREBASE_AUTH_EMULATOR_HOST") ?: "localhost:9099"

        @DynamicPropertySource
        @JvmStatic
        fun firebaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("lika.auth.provider") { "firebase" }
            registry.add("lika.auth.firebase.project-id") { FirebaseProviderTest.PROJECT }
            registry.add("lika.auth.firebase.api-key") { "fake-api-key" }
            registry.add("lika.auth.firebase.emulator-host") { host }
            registry.add("lika.auth.claim-name") { CLAIM }
        }
    }

    private val http = RestClient.create()

    private fun emulator(path: String, body: Any, admin: Boolean = true): JsonNode {
        val toolkit = "http://$host/identitytoolkit.googleapis.com/v1"
        val spec = http.post().uri(java.net.URI.create(if (admin) "$toolkit/projects/${FirebaseProviderTest.PROJECT}/$path" else "$toolkit/$path?key=fake-api-key"))
            .contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(body))
        if (admin) spec.header("Authorization", "Bearer owner")
        return mapper.readTree(spec.retrieve().body(String::class.java))
    }

    private fun customClaims(email: String): JsonNode =
        mapper.readTree(emulator("accounts:lookup", mapOf("email" to listOf(email)))["users"][0].path("customAttributes").asText("{}"))

    private fun claimsOf(token: String): JsonNode = mapper.readTree(Base64.getUrlDecoder().decode(token.split(".")[1]))

    private fun admin(): String {
        val email = "root@lika.test"
        if (anonymous(get("/api/auth/config"))!!["setupRequired"].asBoolean()) {
            val result = mvc.perform(post("/api/auth/setup").json(mapOf("setupToken" to SETUP_TOKEN, "email" to email, "name" to "Root Admin", "password" to PASSWORD))).andReturn().response
            if (result.status == 200) return mapper.readTree(result.contentAsString).access
        }
        return login(email).access
    }

    @Test
    fun `rights are written to the claim of that name, keeping the claims of other applications`() {
        // An account another application already gave a claim, including one named lika
        val email = "${unique("shared")}@lika.test"
        val created = emulator("accounts:signUp", mapOf("email" to email, "password" to PASSWORD, "returnSecureToken" to true), admin = false)
        val id = created["localId"].asText()
        emulator("accounts:update", mapOf("localId" to id, "emailVerified" to true, "customAttributes" to """{"role":"accountant","lika":"theirs"}"""))

        val domain = createDomain()
        val invitation = call(admin(), post("/api/users/invitations").json(mapOf("email" to email, "domains" to mapOf(domain.id to "editor"))))!!
        anonymous(post("/api/auth/invitations/${invitation.linkToken}/accept").json(mapOf("password" to "")), 204)

        val session = login(email)
        val claims = claimsOf(session.access)
        assertEquals("editor", claims[CLAIM]["domains"][domain.id].asText())
        assertEquals("accountant", claims["role"].asText())
        assertEquals("theirs", claims["lika"].asText(), "a claim named lika belonging to another application is left alone")
        createLink(session.access, domain.id)

        call(admin(), delete("/api/users/${session.userId}"), 204)
        val left = customClaims(email)
        assertTrue(left[CLAIM] == null, "the claim of Lika is removed")
        assertEquals("accountant", left["role"].asText())
        assertEquals("theirs", left["lika"].asText())
    }
}
