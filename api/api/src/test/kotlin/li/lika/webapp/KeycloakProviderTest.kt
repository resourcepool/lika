package li.lika.webapp

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile
import java.nio.file.Path
import java.time.Duration
import java.util.Base64

/**
 * Against a real Keycloak (dev/keycloak/lika-realm.json). Skipped without Docker.
 * Browsers sign in with the authorization code flow; tests use the password grant of the dev realm instead.
 */
@Testcontainers(disabledWithoutDocker = true)
class KeycloakProviderTest : ApiTestSupport() {

    companion object {
        @Container
        @JvmStatic
        val keycloak: GenericContainer<*> = GenericContainer("quay.io/keycloak/keycloak:26.4")
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("../../dev/keycloak/lika-realm.json").toAbsolutePath()), "/opt/keycloak/data/import/lika-realm.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/lika/.well-known/openid-configuration").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)))

        private fun url() = "http://${keycloak.host}:${keycloak.getMappedPort(8080)}"

        @DynamicPropertySource
        @JvmStatic
        fun keycloakProperties(registry: DynamicPropertyRegistry) {
            registry.add("lika.auth.provider") { "keycloak" }
            registry.add("lika.auth.keycloak.url") { url() }
            registry.add("lika.auth.keycloak.realm") { "lika" }
            registry.add("lika.auth.keycloak.client-id") { "lika" }
            registry.add("lika.auth.keycloak.client-secret") { "lika-dev-secret" }
        }

        const val ADA = "ada@example.com"
        const val ADA_PASSWORD = "ada password 123"
    }

    private val http = RestClient.create()

    /** Signs in at Keycloak, as the browser would through the sign-in page */
    private fun keycloakLogin(email: String, password: String): JsonNode {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "password"); add("client_id", "lika"); add("client_secret", "lika-dev-secret")
            add("username", email); add("password", password); add("scope", "openid")
        }
        return mapper.readTree(http.post().uri("${url()}/realms/lika/protocol/openid-connect/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(String::class.java))
    }

    private fun refresh(refreshToken: String, expectedStatus: Int = 200) = anonymous(post("/api/auth/refresh").json(mapOf("refreshToken" to refreshToken)), expectedStatus)

    private fun claimsOf(accessToken: String): JsonNode = mapper.readTree(Base64.getUrlDecoder().decode(accessToken.split(".")[1]))

    private fun keycloakAdmin(): String {
        val form = LinkedMultiValueMap<String, String>().apply { add("grant_type", "password"); add("client_id", "admin-cli"); add("username", "admin"); add("password", "admin") }
        return mapper.readTree(http.post().uri("${url()}/realms/master/protocol/openid-connect/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(String::class.java))["access_token"].asText()
    }

    private fun keycloakUser(email: String): JsonNode =
        mapper.readTree(http.get().uri("${url()}/admin/realms/lika/users?exact=true&email=$email").header("Authorization", "Bearer ${keycloakAdmin()}").retrieve().body(String::class.java))[0]

    /** The first admin, created through the setup the first time */
    private fun keycloakAdminSession(): JsonNode {
        val email = "root@lika.test"
        if (anonymous(get("/api/auth/config"))!!["setupRequired"].asBoolean()) {
            anonymous(post("/api/auth/setup").json(mapOf("setupToken" to SETUP_TOKEN, "email" to email, "name" to "Root Admin", "password" to PASSWORD)), 204)
        }
        return keycloakLogin(email, PASSWORD)
    }

    private val JsonNode.kcAccess: String get() = this["access_token"].asText()
    private val JsonNode.kcRefresh: String get() = this["refresh_token"].asText()

    @Test
    fun `the web app gets the sign-in configuration of Keycloak`() {
        val config = anonymous(get("/api/auth/config"))!!
        assertEquals("keycloak", config["provider"].asText())
        assertFalse(config["passwordSignIn"].asBoolean())
        assertEquals("${url()}/realms/lika/protocol/openid-connect/auth", config["sso"]["authorizationEndpoint"].asText())
        assertEquals("lika", config["sso"]["clientId"].asText())
        assertEquals("provider", config["passwordReset"].asText())
        assertEquals("${url()}/realms/lika/account", config["accountUrl"].asText())
    }

    @Test
    fun `the setup creates the admin in Keycloak, whose tokens carry the lika claim`() {
        val session = keycloakAdminSession()
        val claims = claimsOf(session.kcAccess)["lika"]
        assertTrue(claims["admin"].asBoolean(), "the mapper puts the attribute in the token")
        val me = call(session.kcAccess, get("/api/me"))!!
        assertTrue(me["user"]["admin"].asBoolean())
        assertEquals(keycloakUser("root@lika.test")["id"].asText(), me["user"]["externalId"].asText())
        call(session.kcAccess, get("/api/admin/domains"))
    }

    @Test
    fun `an invited user gets a Keycloak account with the rights of the invitation`() {
        val admin = keycloakAdminSession()
        val domain = createDomain()
        val email = "${unique("kc")}@lika.test"
        val invitation = call(admin.kcAccess, post("/api/users/invitations").json(mapOf("email" to email, "name" to "Grace Hopper", "domains" to mapOf(domain.id to "editor"))))!!
        assertFalse(anonymous(get("/api/auth/invitations/${invitation.linkToken}"))!!["accountExists"].asBoolean())
        // No session from Lika: the user now signs in with Keycloak
        anonymous(post("/api/auth/invitations/${invitation.linkToken}/accept").json(mapOf("password" to PASSWORD)), 204)

        val session = keycloakLogin(email, PASSWORD)
        assertEquals("editor", claimsOf(session.kcAccess)["lika"]["domains"][domain.id].asText())
        createLink(session.kcAccess, domain.id)
        assertEquals(listOf(domain.id), call(session.kcAccess, get("/api/domains"))!!.map { it.id })
    }

    @Test
    fun `an existing Keycloak user is linked to their invitation when they sign in`() {
        val admin = keycloakAdminSession()
        val domain = createDomain()
        val invitation = call(admin.kcAccess, post("/api/users/invitations").json(mapOf("email" to ADA, "domains" to mapOf(domain.id to "viewer"))), 200)
            ?: return // invited by a previous run of this test class in the same context
        assertTrue(anonymous(get("/api/auth/invitations/${invitation.linkToken}"))!!["accountExists"].asBoolean())

        // First sign-in: no claim yet; Lika links the account, writes the claim, and renews the session
        val first = keycloakLogin(ADA, ADA_PASSWORD)
        assertTrue(claimsOf(first.kcAccess)["lika"] == null)
        val session = refresh(first.kcRefresh)!!
        assertEquals("active", session["user"]["status"].asText())
        assertEquals("viewer", claimsOf(session.access)["lika"]["domains"][domain.id].asText())
        assertEquals(listOf(domain.id), call(session.access, get("/api/domains"))!!.map { it.id })
    }

    @Test
    fun `nobody gets in without an invitation`() {
        keycloakAdminSession()
        // Create a Keycloak account Lika does not know
        val email = "${unique("stranger")}@lika.test"
        http.post().uri("${url()}/admin/realms/lika/users").header("Authorization", "Bearer ${keycloakAdmin()}").contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("username" to email, "email" to email, "emailVerified" to true, "enabled" to true, "firstName" to "S", "lastName" to "T",
                "credentials" to listOf(mapOf("type" to "password", "value" to PASSWORD, "temporary" to false))))
            .retrieve().toBodilessEntity()
        val session = keycloakLogin(email, PASSWORD)
        assertEquals("not-invited", call(session.kcAccess, get("/api/domains"), 403)!!["errorCode"].asText())
        assertEquals("not-invited", refresh(session.kcRefresh, 403)!!["errorCode"].asText())
    }

    @Test
    fun `rights changes, sign-out and disabling apply immediately`() {
        val admin = keycloakAdminSession()
        val d1 = createDomain()
        val d2 = createDomain()
        val email = "${unique("kc")}@lika.test"
        val invitation = call(admin.kcAccess, post("/api/users/invitations").json(mapOf("email" to email, "domains" to mapOf(d1.id to "viewer"))))!!
        anonymous(post("/api/auth/invitations/${invitation.linkToken}/accept").json(mapOf("password" to PASSWORD)), 204)
        val userId = invitation["user"].id
        val session = keycloakLogin(email, PASSWORD)

        call(admin.kcAccess, put("/api/users/$userId/access").json(mapOf("domains" to mapOf(d2.id to "editor"))))
        assertEquals("token-stale", call(session.kcAccess, get("/api/domains"), 401)!!["errorCode"].asText())
        val renewed = refresh(session.kcRefresh)!!
        assertEquals("editor", claimsOf(renewed.access)["lika"]["domains"][d2.id].asText())
        assertEquals(2, call(renewed.access, get("/api/domains"))!!.size())

        // Sign out everywhere: the Keycloak session is no longer accepted by Lika
        call(admin.kcAccess, post("/api/users/$userId/sessions/revoke"))
        call(renewed.access, get("/api/domains"), 401)
        refresh(renewed.refresh, 401)
        val again = keycloakLogin(email, PASSWORD)
        call(again.kcAccess, get("/api/domains"))

        call(admin.kcAccess, post("/api/users/$userId/disable"))
        call(again.kcAccess, get("/api/domains"), 401)
        // Lika never disables the Keycloak account itself, which other applications may use
        assertTrue(keycloakUser(email)["enabled"].asBoolean())

        call(admin.kcAccess, delete("/api/users/$userId"), 204)
        assertTrue(keycloakUser(email).path("attributes").path("lika").isMissingNode, "the claim is removed")
    }

    @Test
    fun `passwords are managed by Keycloak`() {
        val admin = keycloakAdminSession()
        call(admin.kcAccess, post("/api/me/password").json(mapOf("currentPassword" to PASSWORD, "newPassword" to "another password 1")), 400)
        assertEquals("unsupported-by-provider", anonymous(post("/api/auth/password/forgot").json(mapOf("email" to ADA)), 400)!!["errorCode"].asText())
        assertEquals("unsupported-by-provider", anonymous(post("/api/auth/login").json(mapOf("email" to ADA, "password" to ADA_PASSWORD)), 400)!!["errorCode"].asText())
    }

    @Test
    fun `admin reset links set the password in Keycloak`() {
        val admin = keycloakAdminSession()
        val email = "${unique("kc")}@lika.test"
        val invitation = call(admin.kcAccess, post("/api/users/invitations").json(mapOf("email" to email, "domains" to mapOf(createDomain().id to "viewer"))))!!
        anonymous(post("/api/auth/invitations/${invitation.linkToken}/accept").json(mapOf("password" to PASSWORD)), 204)
        val reset = call(admin.kcAccess, post("/api/users/${invitation["user"].id}/password-reset"))!!
        anonymous(post("/api/auth/password/reset").json(mapOf("token" to reset.linkToken, "password" to "a new password 42")), 204)
        keycloakLogin(email, "a new password 42")
        assertThrows(RestClientResponseException::class.java) { keycloakLogin(email, PASSWORD) }
    }
}
