package li.lika.webapp

import com.fasterxml.jackson.databind.JsonNode
import li.lika.common.models.EngagementResolution
import li.lika.common.persistence.DeeplinkDao
import li.lika.common.persistence.EngagementDao
import li.lika.core.services.EngagementService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class EngagementTest : ApiTestSupport() {

    @Autowired
    lateinit var engagement: EngagementService

    @Autowired
    lateinit var engagementDao: EngagementDao

    @Autowired
    lateinit var deeplinkDao: DeeplinkDao

    private fun visit(domain: JsonNode, link: JsonNode, times: Int = 1) = repeat(times) {
        mvc.perform(get("/${link["slug"].asText()}").header("Host", domain["host"].asText()))
    }

    private fun report(token: String, range: String, domainId: String? = null, linkId: String? = null, expectedStatus: Int = 200): JsonNode? {
        val req = get("/api/engagement").param("range", range)
        domainId?.let { req.param("domainId", it) }
        linkId?.let { req.param("linkId", it) }
        return call(token, req, expectedStatus)
    }

    /** Visits as they would have been counted at [at] */
    private fun visited(link: JsonNode, at: Instant, times: Int) {
        val stored = deeplinkDao.findOne(link.id)!!
        repeat(times) { engagement.record(stored, at) }
    }

    private fun resolutions(link: JsonNode, since: Instant): Map<EngagementResolution, Long> =
        engagementDao.sumByPeriodForLink(link.id, since).groupBy { it.resolution }.mapValues { e -> e.value.sumOf { it.count } }

    private fun utc(date: LocalDate, hour: Int = 0): Instant = date.atTime(hour, 0).toInstant(ZoneOffset.UTC)

    @Test
    fun `visits are counted live per link and per domain`() {
        val domain = createDomain()
        val busy = createLink(ADMIN_TOKEN, domain.id)!!
        val quiet = createLink(ADMIN_TOKEN, domain.id)!!
        val protected = createLink(ADMIN_TOKEN, domain.id, mapOf("password" to mapOf("password" to "s3cret")))!!
        val expired = createLink(ADMIN_TOKEN, domain.id, mapOf("expiry" to mapOf("expiryDate" to LocalDate.now().minusDays(1).toString())))!!
        visit(domain, busy, 3)
        visit(domain, quiet)
        // Asking for the password is a visit; a link which does not resolve has none
        visit(domain, protected)
        visit(domain, expired)
        mvc.perform(get("/${unique("missing")}").header("Host", domain["host"].asText()))

        val live = report(ADMIN_TOKEN, "24h", domain.id)!!
        assertEquals("hour", live["resolution"].asText())
        assertEquals(24, live["series"].size())
        assertEquals(5, live["total"].asInt())
        assertEquals(5, live["series"].last()["count"].asInt(), "counted in the current hour")
        assertEquals(listOf(busy.id, protected.id, quiet.id).take(1), live["links"].map { it["link"]["id"].asText() }.take(1))
        assertEquals(3, live["links"][0]["count"].asInt())
        assertEquals(3, live["links"].size())
        assertEquals(5, live["domains"].single()["count"].asInt())
        assertFalse(live.toString().contains("passwordHash"))

        val ofLink = report(ADMIN_TOKEN, "30d", linkId = busy.id)!!
        assertEquals("day", ofLink["resolution"].asText())
        assertEquals(30, ofLink["series"].size())
        assertEquals(3, ofLink["total"].asInt())
        assertEquals(busy.id, ofLink["link"]["id"].asText())
        assertTrue(ofLink["links"].isEmpty)

        assertEquals(52, report(ADMIN_TOKEN, "12m", domain.id)!!["series"].size())
        report(ADMIN_TOKEN, "1y", domain.id, expectedStatus = 400)
    }

    @Test
    fun `engagement follows the rights on domains`() {
        val mine = createDomain()
        val other = createDomain()
        val link = createLink(ADMIN_TOKEN, mine.id)!!
        val hidden = createLink(ADMIN_TOKEN, other.id)!!
        visit(mine, link, 2)
        visit(other, hidden, 4)
        val viewer = createKey(mapOf(mine.id to "viewer")).secret

        val all = report(viewer, "7d")!!
        assertEquals(2, all["total"].asInt())
        assertEquals(listOf(mine.id), all["domains"].map { it["domainId"].asText() })
        assertEquals(listOf(link.id), all["links"].map { it["link"]["id"].asText() })
        report(viewer, "7d", domainId = other.id, expectedStatus = 404)
        report(viewer, "7d", linkId = hidden.id, expectedStatus = 404)
        assertEquals(6, report(ADMIN_TOKEN, "7d")!!["domains"].filter { it["domainId"].asText() in setOf(mine.id, other.id) }.sumOf { it["count"].asInt() })
    }

    @Test
    fun `visits are kept per hour for a day, per day for a month, per week for a year`() {
        val domain = createDomain()
        val link = createLink(ADMIN_TOKEN, domain.id)!!
        val now = utc(LocalDate.of(2026, 11, 19), 15)
        val today = LocalDate.of(2026, 11, 19)
        visited(link, now.minus(1, ChronoUnit.HOURS), 2)               // Today
        visited(link, utc(today.minusDays(1), 9), 3)                   // Yesterday before 15:00: older than 24 hours, in a day which is not
        visited(link, utc(today.minusDays(2), 8), 4)                   // Two hours of the day before
        visited(link, utc(today.minusDays(2), 20), 1)
        visited(link, utc(LocalDate.of(2026, 10, 1), 12), 5)           // First day of the previous month
        visited(link, utc(LocalDate.of(2026, 9, 22), 12), 6)           // Tuesday and Thursday of a week of September
        visited(link, utc(LocalDate.of(2026, 9, 24), 12), 7)
        visited(link, utc(LocalDate.of(2025, 11, 10), 12), 8)          // Over a year ago

        engagement.consolidate(now)

        val kept = resolutions(link, Instant.EPOCH)
        assertEquals(mapOf(EngagementResolution.HOUR to 5L, EngagementResolution.DAY to 10L, EngagementResolution.WEEK to 13L), kept)
        val periods = engagementDao.sumByPeriodForLink(link.id, Instant.EPOCH).associate { it.start to it.count }
        assertEquals(5, periods[utc(today.minusDays(2))], "one bucket for the day")
        assertEquals(5, periods[utc(LocalDate.of(2026, 10, 1))], "the previous month stays per day")
        assertEquals(13, periods[utc(LocalDate.of(2026, 9, 21))], "one bucket for the week, on its Monday")

        // Again: nothing is counted twice
        engagement.consolidate(now)
        assertEquals(kept, resolutions(link, Instant.EPOCH))

        // Each report adds up what is kept, whatever its resolution
        assertEquals(28, engagementDao.sumByPeriodForLink(link.id, Instant.EPOCH).sumOf { it.count })
        val year = engagement.report(li.lika.common.security.LikaPrincipal.KeyPrincipal("k", "k", li.lika.common.models.LikaClaims(0, true, emptyMap())), null, link.id, li.lika.common.models.EngagementRange.LAST_52_WEEKS, now)
        assertEquals(28, year.total)
        assertEquals(52, year.series.size)
        assertEquals(13, year.series.first { it.start == utc(LocalDate.of(2026, 9, 21)) }.count)
        assertEquals(10, year.series.last().count, "this week: today, yesterday and the day before")
    }

    @Test
    fun `links unused for a year are listed for cleanup`() {
        val domain = createDomain()
        val other = createDomain()
        fun old(link: JsonNode): JsonNode {
            val stored = deeplinkDao.findOne(link.id)!!
            deeplinkDao.save(stored.copy(createdDateTime = stored.createdDateTime.minusYears(2)))
            return link
        }
        val forgotten = old(createLink(ADMIN_TOKEN, domain.id)!!)
        val used = old(createLink(ADMIN_TOKEN, domain.id)!!)
        val recent = createLink(ADMIN_TOKEN, domain.id)!!
        val elsewhere = old(createLink(ADMIN_TOKEN, other.id)!!)
        visit(domain, used)

        val unused = call(ADMIN_TOKEN, get("/api/links/unused").param("domainId", domain.id))!!
        assertEquals(listOf(forgotten.id), unused["links"].map { it["id"].asText() })
        assertEquals(1, unused["total"].asInt())
        // Visits are counted since this test run: not for a year yet
        assertEquals(unused["trackedSince"].asText(), unused["since"].asText())
        assertFalse(unused.toString().contains(recent.id))

        val viewer = createKey(mapOf(domain.id to "viewer")).secret
        assertEquals(listOf(forgotten.id), call(viewer, get("/api/links/unused"))!!["links"].map { it["id"].asText() })
        call(viewer, get("/api/links/unused").param("domainId", other.id), 404)
        assertTrue(call(ADMIN_TOKEN, get("/api/links/unused"))!!["links"].map { it["id"].asText() }.containsAll(listOf(forgotten.id, elsewhere.id)))
    }

    @Test
    fun `deleting a domain deletes its visits`() {
        val domain = createDomain()
        val link = createLink(ADMIN_TOKEN, domain.id)!!
        visit(domain, link)
        assertEquals(1, report(ADMIN_TOKEN, "24h", domain.id)!!["total"].asInt())
        call(ADMIN_TOKEN, delete("/api/admin/domains/${domain.id}").param("force", "true"))
        assertTrue(engagementDao.sumByPeriodForLink(link.id, Instant.EPOCH).isEmpty())
    }
}
