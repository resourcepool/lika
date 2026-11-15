package li.lika.webapp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.time.LocalDate

/**
 * A link not found tells why in the logs, with the slug, the domain and the headers the domain came from.
 */
@ExtendWith(OutputCaptureExtension::class)
@TestPropertySource(properties = ["logging.level.li.lika.notfound=DEBUG"])
class LinkNotFoundLogTest : ApiTestSupport() {

    @Test
    fun `an unknown slug logs the domain looked up and the headers it came from`(output: CapturedOutput) {
        val domain = createDomain()
        val host = domain["host"].asText()
        mvc.perform(get("/nope-xyz").header("Host", "lika-origin.test").header(CDN_HOST_HEADER, host))
        assertTrue(output.out.contains("Link not found: no link /nope-xyz on domain $host (id ${domain.id})"), output.out.takeLast(800))
        assertTrue(output.out.contains("GET /nope-xyz; $CDN_HOST_HEADER=$host, X-Forwarded-Host=-, Host=lika-origin.test; domain looked up: $host"))
    }

    @Test
    fun `an unregistered host says so`(output: CapturedOutput) {
        mvc.perform(get("/epf").header("Host", "lika-origin.test"))
        assertTrue(output.out.contains("no domain is registered for host lika-origin.test (looking for /epf)"))
    }

    @Test
    fun `an expired link says when it expired`(output: CapturedOutput) {
        val domain = createDomain()
        val yesterday = LocalDate.now().minusDays(1)
        val slug = createLink(ADMIN_TOKEN, domain.id, mapOf("expiry" to mapOf("expiryDate" to yesterday.toString())))!!["slug"].asText()
        mvc.perform(get("/$slug").header("Host", domain["host"].asText()))
        assertTrue(output.out.contains("/$slug on ${domain["host"].asText()} expired on $yesterday"))
    }


    @Test
    fun `the client never learns the domain of a link it cannot read`(output: CapturedOutput) {
        val hidden = createDomain()
        val link = createLink(ADMIN_TOKEN, hidden.id)!!
        val outsider = createKey(mapOf(createDomain().id to "viewer"))
        val body = call(outsider.secret, get("/api/links/${link.id}"), 404)!!
        assertFalse(body.toString().contains(hidden["host"].asText()))
        assertEquals("slug-not-found", body["errorCode"].asText())
        assertTrue(output.out.contains("link ${link.id} is on ${hidden["host"].asText()}, which ${outsider["key"]["name"].asText()}"))
    }
}
