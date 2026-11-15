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
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64

/**
 * Against the Firebase Auth emulator: `firebase emulators:start --only auth` in dev/firebase,
 * or FIREBASE_AUTH_EMULATOR_HOST. Skipped when it is not running.
 */
@EnabledIf("li.lika.webapp.FirebaseProviderTest#emulatorRunning")
class FirebaseProviderTest : ApiTestSupport() {

    companion object {
        const val PROJECT = "demo-lika"
        private val host: String = System.getenv("FIREBASE_AUTH_EMULATOR_HOST") ?: "localhost:9099"

        @JvmStatic
        fun emulatorRunning(): Boolean = runCatching {
            Socket().use { it.connect(InetSocketAddress(host.substringBefore(':'), host.substringAfter(':').toInt()), 500) }
        }.isSuccess

        @DynamicPropertySource
        @JvmStatic
        fun firebaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("lika.auth.provider") { "firebase" }
            registry.add("lika.auth.firebase.project-id") { PROJECT }
            registry.add("lika.auth.firebase.api-key") { "fake-api-key" }
            registry.add("lika.auth.firebase.emulator-host") { host }
        }
    }

    private val http = RestClient.create()
    private val toolkit = "http://$host/identitytoolkit.googleapis.com/v1"

    private fun emulator(path: String, body: Any, admin: Boolean = true): JsonNode {
        val spec = http.post().uri(java.net.URI.create(if (admin) "$toolkit/projects/$PROJECT/$path" else "$toolkit/$path?key=fake-api-key"))
            .contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(body))
        if (admin) spec.header("Authorization", "Bearer owner")
        return mapper.readTree(spec.retrieve().body(String::class.java))
    }

    private fun firebaseUser(email: String): JsonNode? = emulator("accounts:lookup", mapOf("email" to listOf(email)))["users"]?.get(0)

    private fun claimsOf(token: String): JsonNode = mapper.readTree(Base64.getUrlDecoder().decode(token.split(".")[1]))

    private fun firebaseAdminSession(): JsonNode {
        val email = "root@lika.test"
        if (anonymous(get("/api/auth/config"))!!["setupRequired"].asBoolean()) {
            val result = mvc.perform(post("/api/auth/setup").json(mapOf("setupToken" to SETUP_TOKEN, "email" to email, "name" to "Root Admin", "password" to PASSWORD))).andReturn().response
            // 204: the emulator kept the account of a previous run, which was linked and keeps its password
            if (result.status == 200) return mapper.readTree(result.contentAsString)
            assertEquals(204, result.status)
        }
        return login(email)
    }

    private fun inviteAndAccept(domains: Map<String, String>): JsonNode {
        val email = "${unique("fb")}@lika.test"
        val invitation = call(firebaseAdminSession().access, post("/api/users/invitations").json(mapOf("email" to email, "domains" to domains)))!!
        return anonymous(post("/api/auth/invitations/${invitation.linkToken}/accept").json(mapOf("password" to PASSWORD)))!!
    }

    @Test
    fun `the web app signs in with email and password`() {
        val config = anonymous(get("/api/auth/config"))!!
        assertEquals("firebase", config["provider"].asText())
        assertTrue(config["passwordSignIn"].asBoolean())
        assertTrue(config["sso"].isNull, "Google sign-in needs its OAuth client")
        assertEquals("email", config["passwordReset"].asText(), "Firebase emails reset links itself")
    }

    @Test
    fun `the setup creates the admin in Firebase, whose tokens carry the custom claim`() {
        val session = firebaseAdminSession()
        assertTrue(claimsOf(session.access)["lika"]["admin"].asBoolean())
        assertEquals(firebaseUser("root@lika.test")!!["localId"].asText(), session["user"]["externalId"].asText())
        call(session.access, get("/api/admin/domains"))
    }

    @Test
    fun `an invited user gets a Firebase account and a session`() {
        val domain = createDomain()
        val session = inviteAndAccept(mapOf(domain.id to "editor"))
        assertEquals("editor", claimsOf(session.access)["lika"]["domains"][domain.id].asText())
        createLink(session.access, domain.id)
        assertEquals(session.userId, login(session.email)["user"]["id"].asText())
        assertEquals("invalid-credentials", login(session.email, "wrong password", 401)["errorCode"].asText())
    }

    @Test
    fun `an existing Firebase account is linked when its owner signs in`() {
        val email = "${unique("existing")}@lika.test"
        val created = emulator("accounts:signUp", mapOf("email" to email, "password" to PASSWORD, "returnSecureToken" to true), admin = false)
        emulator("accounts:update", mapOf("localId" to created["localId"].asText(), "emailVerified" to true))
        val domain = createDomain()
        val invitation = call(firebaseAdminSession().access, post("/api/users/invitations").json(mapOf("email" to email, "domains" to mapOf(domain.id to "viewer"))))!!
        assertTrue(anonymous(get("/api/auth/invitations/${invitation.linkToken}"))!!["accountExists"].asBoolean())
        // Their password stays theirs: they sign in with it
        anonymous(post("/api/auth/invitations/${invitation.linkToken}/accept").json(mapOf("password" to "")), 204)

        val session = login(email)
        assertEquals(created["localId"].asText(), session["user"]["externalId"].asText())
        assertEquals("viewer", claimsOf(session.access)["lika"]["domains"][domain.id].asText())
    }

    @Test
    fun `nobody gets in without an invitation`() {
        val email = "${unique("stranger")}@lika.test"
        val created = emulator("accounts:signUp", mapOf("email" to email, "password" to PASSWORD, "returnSecureToken" to true), admin = false)
        emulator("accounts:update", mapOf("localId" to created["localId"].asText(), "emailVerified" to true))
        assertEquals("not-invited", login(email, PASSWORD, 403)["errorCode"].asText())
        assertEquals("not-invited", call(created["idToken"].asText(), get("/api/domains"), 403)!!["errorCode"].asText())
    }

    @Test
    fun `rights changes, sign-out and disabling apply immediately`() {
        val d1 = createDomain()
        val d2 = createDomain()
        val user = inviteAndAccept(mapOf(d1.id to "viewer"))
        val admin = firebaseAdminSession().access

        call(admin, put("/api/users/${user.userId}/access").json(mapOf("domains" to mapOf(d2.id to "editor"))))
        assertEquals("token-stale", call(user.access, get("/api/domains"), 401)!!["errorCode"].asText())
        val renewed = anonymous(post("/api/auth/refresh").json(mapOf("refreshToken" to user.refresh)))!!
        assertEquals("editor", claimsOf(renewed.access)["lika"]["domains"][d2.id].asText())

        // Signed in before the revocation: refused, even renewed
        Thread.sleep(1100)
        call(admin, post("/api/users/${user.userId}/sessions/revoke"))
        call(renewed.access, get("/api/domains"), 401)
        anonymous(post("/api/auth/refresh").json(mapOf("refreshToken" to renewed.refresh)), 401)
        call(login(user.email).access, get("/api/domains"))

        call(admin, post("/api/users/${user.userId}/disable"))
        assertEquals("user-disabled", login(user.email, PASSWORD, 403)["errorCode"].asText())
        assertFalse(firebaseUser(user.email)!!.path("disabled").asBoolean(), "the Firebase account itself stays enabled")

        call(admin, delete("/api/users/${user.userId}"), 204)
        assertFalse(firebaseUser(user.email)!!.path("customAttributes").asText("{}").contains("lika"), "the claim is removed")
    }

    @Test
    fun `users change and reset their password in Firebase`() {
        val user = inviteAndAccept(mapOf(createDomain().id to "viewer"))
        val changed = call(user.access, post("/api/me/password").json(mapOf("currentPassword" to PASSWORD, "newPassword" to "another password 1")))!!
        call(changed.access, get("/api/me"))
        login(user.email, PASSWORD, 401)
        login(user.email, "another password 1")

        val reset = call(firebaseAdminSession().access, post("/api/users/${user.userId}/password-reset"))!!
        anonymous(post("/api/auth/password/reset").json(mapOf("token" to reset.linkToken, "password" to "a third password 2")), 204)
        login(user.email, "a third password 2")
        anonymous(post("/api/auth/password/forgot").json(mapOf("email" to user.email)), 204)
    }

    @Test
    fun `custom claims beyond the Firebase limit are refused`() {
        val user = inviteAndAccept(mapOf(createDomain().id to "viewer"))
        val many = (1..25).associate { createDomain().id to "editor" }
        val refused = call(firebaseAdminSession().access, put("/api/users/${user.userId}/access").json(mapOf("domains" to many)), 400)!!
        assertTrue(refused["message"].asText().contains("1000"))
    }
}
