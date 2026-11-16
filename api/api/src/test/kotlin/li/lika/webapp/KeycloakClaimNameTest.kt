package li.lika.webapp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64

/**
 * LIKA_CLAIM_NAME with Keycloak. The realm starts with the mapper of an install with the default claim name:
 * Lika updates it to the attribute and claim of that name. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class KeycloakClaimNameTest : ApiTestSupport() {

    companion object {
        const val CLAIM = "acme_lika"

        /** dev realm, plus the mapper Lika created with the default claim name */
        private fun realm(): String {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val realm = mapper.readTree(Files.readString(Path.of("../../dev/keycloak/lika-realm.json"))) as ObjectNode
            val client = realm["clients"][0] as ObjectNode
            client.putArray("protocolMappers").add(mapper.valueToTree<JsonNode>(mapOf(
                "name" to "lika rights", "protocol" to "openid-connect", "protocolMapper" to "oidc-usermodel-attribute-mapper",
                "config" to mapOf("user.attribute" to "lika", "claim.name" to "lika", "jsonType.label" to "JSON",
                    "access.token.claim" to "true", "id.token.claim" to "true", "userinfo.token.claim" to "true")
            )))
            return mapper.writeValueAsString(realm)
        }

        @Container
        @JvmStatic
        val keycloak: GenericContainer<*> = GenericContainer("quay.io/keycloak/keycloak:26.4")
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCopyToContainer(Transferable.of(realm()), "/opt/keycloak/data/import/lika-realm.json")
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
            registry.add("lika.auth.claim-name") { CLAIM }
        }
    }

    private val http = RestClient.create()

    private fun token(realm: String, form: Map<String, String>): JsonNode {
        val body = LinkedMultiValueMap<String, String>().apply { form.forEach { (k, v) -> add(k, v) } }
        return mapper.readTree(http.post().uri("${url()}/realms/$realm/protocol/openid-connect/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body).retrieve().body(String::class.java))
    }

    private fun keycloakLogin(email: String, password: String): String = token("lika", mapOf("grant_type" to "password", "client_id" to "lika",
        "client_secret" to "lika-dev-secret", "username" to email, "password" to password, "scope" to "openid"))["access_token"].asText()

    private fun keycloakGet(path: String): JsonNode {
        val admin = token("master", mapOf("grant_type" to "password", "client_id" to "admin-cli", "username" to "admin", "password" to "admin"))["access_token"].asText()
        return mapper.readTree(http.get().uri("${url()}/admin/realms/lika$path").header("Authorization", "Bearer $admin").retrieve().body(String::class.java))
    }

    private fun claimsOf(token: String): JsonNode = mapper.readTree(Base64.getUrlDecoder().decode(token.split(".")[1]))

    @Test
    fun `rights travel in the attribute and claim of that name`() {
        if (anonymous(get("/api/auth/config"))!!["setupRequired"].asBoolean()) {
            anonymous(post("/api/auth/setup").json(mapOf("setupToken" to SETUP_TOKEN, "email" to "root@lika.test", "name" to "Root Admin", "password" to PASSWORD)), 204)
        }
        val admin = keycloakLogin("root@lika.test", PASSWORD)
        assertTrue(claimsOf(admin)[CLAIM]["admin"].asBoolean())
        assertNull(claimsOf(admin)["lika"], "the default claim is gone")
        call(admin, get("/api/admin/domains"))

        val user = keycloakGet("/users?exact=true&email=root@lika.test")[0]
        assertFalse(user["attributes"][CLAIM].isNull)
        assertTrue(keycloakGet("/users/profile")["attributes"].any { it["name"].asText() == CLAIM })

        val client = keycloakGet("/clients?clientId=lika")[0]["id"].asText()
        val mappers = keycloakGet("/clients/$client/protocol-mappers/models").filter { it["name"].asText() == "lika rights" }
        assertEquals(1, mappers.size, "the existing mapper is updated, not duplicated")
        assertEquals(CLAIM, mappers[0]["config"]["claim.name"].asText())
        assertEquals(CLAIM, mappers[0]["config"]["user.attribute"].asText())
    }
}
