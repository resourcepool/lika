package li.lika.common.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * How fine visits are counted. Periods are aligned on UTC: hours, days, and weeks starting on Monday.
 * Visits are recorded per hour, then kept per day, then per week as they age: see EngagementService.
 */
enum class EngagementResolution(val code: String) {
    @JsonProperty("hour") HOUR("H"),
    @JsonProperty("day") DAY("D"),
    @JsonProperty("week") WEEK("W");

    /** Start of the period holding [instant] */
    fun startOf(instant: Instant): Instant = when (this) {
        HOUR -> instant.truncatedTo(ChronoUnit.HOURS)
        DAY -> instant.truncatedTo(ChronoUnit.DAYS)
        WEEK -> instant.atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZoneOffset.UTC).toInstant()
    }

    fun next(start: Instant): Instant = when (this) {
        HOUR -> start.plus(1, ChronoUnit.HOURS)
        DAY -> start.plus(1, ChronoUnit.DAYS)
        WEEK -> start.plus(7, ChronoUnit.DAYS)
    }

    companion object {
        fun ofCode(code: String): EngagementResolution = entries.first { it.code == code }
    }
}

/**
 * Visits of a link during the period of [resolution] starting at [start].
 * @param host domain of the link when it was visited, to sum visits per domain
 */
data class EngagementBucket(
    val linkId: String,
    val host: String,
    val resolution: EngagementResolution,
    val start: Instant,
    val count: Long
)

/** Visits during a period, all links together */
data class EngagementPeriod(val resolution: EngagementResolution, val start: Instant, val count: Long)

enum class EngagementRange(val resolution: EngagementResolution, val periods: Long) {
    /** Live: the last 24 hours, per hour */
    @JsonProperty("24h") LAST_24_HOURS(EngagementResolution.HOUR, 24),
    @JsonProperty("7d") LAST_7_DAYS(EngagementResolution.DAY, 7),
    @JsonProperty("30d") LAST_30_DAYS(EngagementResolution.DAY, 30),
    @JsonProperty("90d") LAST_13_WEEKS(EngagementResolution.WEEK, 13),
    /** Everything Lika keeps */
    @JsonProperty("12m") LAST_52_WEEKS(EngagementResolution.WEEK, 52);

    /** Start of the first period: the last one is the current hour, day or week */
    fun from(now: Instant): Instant {
        var start = resolution.startOf(now)
        // Weeks and days all last the same in UTC
        val unit = if (resolution == EngagementResolution.HOUR) ChronoUnit.HOURS else ChronoUnit.DAYS
        val length = if (resolution == EngagementResolution.WEEK) 7L else 1L
        start = start.minus((periods - 1) * length, unit)
        return start
    }

    companion object {
        fun parse(value: String?): EngagementRange = when (value?.lowercase()) {
            null, "", "30d" -> LAST_30_DAYS
            "24h" -> LAST_24_HOURS
            "7d" -> LAST_7_DAYS
            "90d" -> LAST_13_WEEKS
            "12m" -> LAST_52_WEEKS
            else -> throw IllegalArgumentException("Unknown range $value: 24h, 7d, 30d, 90d or 12m")
        }
    }
}

data class EngagementPoint(val start: Instant, val count: Long)

data class DomainEngagement(val domainId: String, val host: String, val count: Long)

data class LinkEngagement(val link: Deeplink, val count: Long)

/**
 * Visits of the links of one domain, of every domain the caller reads, or of one link.
 * @param series one point per period from [from], without gap: periods without visits count 0
 * @param domains visits per domain, most visited first; empty for one link
 * @param links most visited links; empty for one link
 * @param link the link, when the report is about one
 * @param trackedSince when Lika started counting visits
 */
data class EngagementReport(
    val range: EngagementRange,
    val resolution: EngagementResolution,
    val from: Instant,
    val to: Instant,
    val total: Long,
    val series: List<EngagementPoint>,
    val domains: List<DomainEngagement> = emptyList(),
    val links: List<LinkEngagement> = emptyList(),
    val link: Deeplink? = null,
    val trackedSince: Instant? = null
)

/**
 * Links nobody visited for a year, candidates for a cleanup.
 * @param since no visit since then: a year ago, or [trackedSince] when Lika has counted visits for less than a year
 * @param total how many there are; [links] holds the oldest ones
 */
data class UnusedLinks(
    val since: Instant,
    val trackedSince: Instant,
    val total: Int,
    val links: List<Deeplink>
)
