package li.lika.webapp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDate
import java.time.ZonedDateTime

class PublicLinksTest : ApiTestSupport() {

    private fun visit(slug: String) = mvc.perform(get("/$slug").header("Host", STANDARD_HOST))

    private fun unlock(slug: String, password: String) =
        mvc.perform(post("/$slug").header("Host", STANDARD_HOST).param("password", password))

    @Test
    fun `passwords are hashed, never returned, and checked by the server`() {
        val link = createLink(ADMIN_TOKEN, standardDomainId(), mapOf("password" to mapOf("password" to "s3cret")))!!
        val slug = link["slug"].asText()
        assertTrue(link["options"]["password"]["enabled"].asBoolean())
        assertFalse(link.toString().contains("s3cret"))
        assertNull(link["options"]["password"]["passwordHash"])

        val page = visit(slug).andExpect(status().isOk).andReturn().response.contentAsString
        assertFalse(page.contains(ADMIN_TOKEN), "the password page must not embed any token")

        unlock(slug, "wrong").andExpect(status().isUnauthorized)
        unlock(slug, "s3cret").andExpect(status().isFound).andExpect(redirectedUrl("https://example.com/$slug"))
    }

    @Test
    fun `editing without password options keeps the password, remove drops it`() {
        val domainId = standardDomainId()
        val link = createLink(ADMIN_TOKEN, domainId, mapOf("password" to mapOf("password" to "s3cret")))!!
        val slug = link["slug"].asText()
        val base = mapOf("id" to link.id, "targetUrl" to "https://example.com/$slug", "domainId" to domainId, "slug" to slug)

        val kept = call(ADMIN_TOKEN, put("/api/links").json(base))!!
        assertTrue(kept["options"]["password"]["enabled"].asBoolean())

        val removed = call(ADMIN_TOKEN, put("/api/links").json(base + ("options" to mapOf("password" to mapOf("remove" to true)))))!!
        assertTrue(removed["options"]["password"].isNull)
        visit(slug).andExpect(status().isFound)
    }

    @Test
    fun `links redirect to the fallback URL before activation`() {
        val activation = mapOf("activationInstant" to ZonedDateTime.now().plusDays(1).toString(), "targetUrlBeforeActivation" to "https://soon.test")
        val link = createLink(ADMIN_TOKEN, standardDomainId(), mapOf("activation" to activation))!!
        visit(link["slug"].asText()).andExpect(redirectedUrl("https://soon.test"))
    }

    @Test
    fun `links not active yet without fallback are not found`() {
        val activation = mapOf("activationInstant" to ZonedDateTime.now().plusDays(1).toString())
        val link = createLink(ADMIN_TOKEN, standardDomainId(), mapOf("activation" to activation))!!
        visit(link["slug"].asText()).andExpect(status().isOk).andExpect(forwardedUrl("/static/error.html"))
    }

    @Test
    fun `active links redirect to their target`() {
        val activation = mapOf("activationInstant" to ZonedDateTime.now().minusDays(1).toString(), "targetUrlBeforeActivation" to "https://soon.test")
        val slug = createLink(ADMIN_TOKEN, standardDomainId(), mapOf("activation" to activation))!!["slug"].asText()
        visit(slug).andExpect(redirectedUrl("https://example.com/$slug"))
    }

    @Test
    fun `expired links are not found as soon as they expire`() {
        val expired = createLink(ADMIN_TOKEN, standardDomainId(), mapOf("expiry" to mapOf("expiryDate" to LocalDate.now().minusDays(1).toString())))!!
        visit(expired["slug"].asText()).andExpect(forwardedUrl("/static/error.html"))

        val lastDay = createLink(ADMIN_TOKEN, standardDomainId(), mapOf("expiry" to mapOf("expiryDate" to LocalDate.now().toString())))!!
        visit(lastDay["slug"].asText()).andExpect(status().isFound)
    }
}
