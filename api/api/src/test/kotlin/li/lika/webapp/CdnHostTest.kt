package li.lika.webapp

import li.lika.webapp.support.RequestHost
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * https://cdn-domain/slug served by a CDN from https://lika-origin/slug: the slug is looked up on the CDN domain.
 */
class CdnHostTest : ApiTestSupport() {

    private val origin = "lika-origin.test"

    private fun cdnDomainWithLink(): Pair<String, String> {
        val domain = createDomain()
        val slug = createLink(ADMIN_TOKEN, domain.id)!!["slug"].asText()
        return domain["host"].asText() to slug
    }

    @Test
    fun `X-Forwarded-Host of the CDN wins over the Host of the origin`() {
        val (host, slug) = cdnDomainWithLink()
        mvc.perform(get("/$slug").header("Host", origin).header("X-Forwarded-Host", host))
            .andExpect(status().isFound).andExpect(redirectedUrl("https://example.com/$slug"))
        mvc.perform(get("/$slug").header("Host", origin)).andExpect(forwardedUrl("/static/error.html"))
    }

    @Test
    fun `the first of several forwarded hosts is the client's`() {
        val (host, slug) = cdnDomainWithLink()
        mvc.perform(get("/$slug").header("Host", origin).header("X-Forwarded-Host", "${host.uppercase()}, $origin"))
            .andExpect(redirectedUrl("https://example.com/$slug"))
    }

    @Test
    fun `the dedicated header survives a proxy overwriting X-Forwarded-Host`() {
        val (host, slug) = cdnDomainWithLink()
        // Traefik replaced X-Forwarded-Host with what it received: the origin
        mvc.perform(get("/$slug").header("Host", origin).header("X-Forwarded-Host", origin).header(CDN_HOST_HEADER, host))
            .andExpect(redirectedUrl("https://example.com/$slug"))
    }

    @Test
    fun `password-protected links unlock on the CDN domain`() {
        val domain = createDomain()
        val slug = createLink(ADMIN_TOKEN, domain.id, mapOf("password" to mapOf("password" to "s3cret")))!!["slug"].asText()
        mvc.perform(post("/$slug").header("Host", origin).header(CDN_HOST_HEADER, domain["host"].asText()).param("password", "s3cret"))
            .andExpect(redirectedUrl("https://example.com/$slug"))
    }

    @Test
    fun `search by host uses the same host`() {
        val (host, slug) = cdnDomainWithLink()
        call(ADMIN_TOKEN, get("/api/search/slug/$slug").header("Host", origin).header("X-Forwarded-Host", host))
    }

    @Test
    fun `normalizes host values`() {
        assertEquals("go.example.com", RequestHost.normalize(" Go.Example.com , origin.test"))
        assertEquals("localhost:8080", RequestHost.normalize("localhost:8080"))
        assertNull(RequestHost.normalize(" "))
        assertNull(RequestHost.normalize(null))
    }
}
