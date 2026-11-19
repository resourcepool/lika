package li.lika.core.services

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import li.lika.common.models.*
import li.lika.common.persistence.DeeplinkDao
import li.lika.common.persistence.EngagementDao
import li.lika.common.security.LikaPrincipal
import li.lika.core.services.handlers.TransactionHandler
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Counts the visits of links, and reports them per domain and per link.
 *
 * Visits are counted per hour. As they age, they are kept less fine, by [consolidate]:
 * - the last 24 hours stay per hour, for the live view;
 * - days entirely older than that are kept per day;
 * - beyond the previous month, weeks are kept per week;
 * - after a year, they are deleted.
 * Every period is in UTC.
 */
@Service
class EngagementService(
    private val engagementDao: EngagementDao,
    private val deeplinkDao: DeeplinkDao,
    private val deeplinkService: DeeplinkService,
    private val authorization: AuthorizationService,
    private val transactionHandler: TransactionHandler
) {
    private val logger = LoggerFactory.getLogger(EngagementService::class.java)

    companion object {
        /** A little over a year, so that the report of the last 52 weeks is whole */
        const val RETENTION_DAYS = 366L
        const val TOP_LINKS = 20
        const val MAX_UNUSED_LINKS = LinkBatchRequest.MAX_LINKS
        /** Days kept per day at least, whatever the length of the months: the 30 days report needs them */
        private const val MIN_DAILY_DAYS = 31L
    }

    private data class Hour(val linkId: String, val host: String, val start: Instant)

    /** Visits not written yet: a visit costs no database write, [flush] writes them together */
    private val pending = ConcurrentHashMap<Hour, Long>()

    @PostConstruct
    fun init() {
        if (engagementDao.trackedSince() == null) {
            engagementDao.markTrackedSince(Instant.now())
        }
    }

    /** A visitor followed the short URL of [link] */
    fun record(link: Deeplink, at: Instant = Instant.now()) {
        pending.merge(Hour(link.id, link.host, EngagementResolution.HOUR.startOf(at)), 1L, Long::plus)
    }

    @Scheduled(fixedDelay = 10_000)
    @PreDestroy
    @Synchronized
    fun flush() {
        for (hour in pending.keys.toList()) {
            val count = pending.remove(hour) ?: continue
            try {
                engagementDao.add(EngagementBucket(hour.linkId, hour.host, EngagementResolution.HOUR, hour.start, count))
            } catch (e: Exception) {
                logger.warn("Could not save {} visits of link {}, will retry: {}", count, hour.linkId, e.message)
                pending.merge(hour, count, Long::plus)
            }
        }
    }

    @EventListener(ApplicationReadyEvent::class)
    fun consolidateOnStart() = consolidate()

    /** Each night, once the day before yesterday is entirely older than 24 hours */
    @Scheduled(cron = "0 30 0 * * *", zone = "UTC")
    fun consolidate() = consolidate(Instant.now())

    @Synchronized
    fun consolidate(now: Instant) {
        flush()
        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        // 1. The last 24 hours stay per hour: only days entirely older become days
        merge(EngagementResolution.HOUR, EngagementResolution.DAY, EngagementResolution.DAY.startOf(now.minus(24, ChronoUnit.HOURS)))
        // 2. This month and the previous one stay per day: only weeks entirely older become weeks
        val daily = minOf(today.minusMonths(1).withDayOfMonth(1), today.minusDays(MIN_DAILY_DAYS))
        merge(EngagementResolution.DAY, EngagementResolution.WEEK, EngagementResolution.WEEK.startOf(daily.atStartOfDay(ZoneOffset.UTC).toInstant()))
        // 3. A year is kept
        engagementDao.deleteBefore(today.minusDays(RETENTION_DAYS).atStartOfDay(ZoneOffset.UTC).toInstant())
    }

    /**
     * Replaces the buckets of [fine] resolution starting before [before], a start of [coarse] period, by [coarse] ones.
     */
    private fun merge(fine: EngagementResolution, coarse: EngagementResolution, before: Instant) {
        engagementDao.periodStarts(fine, before).map { coarse.startOf(it) }.distinct().sorted().forEach { start ->
            try {
                transactionHandler.runInTransaction {
                    val buckets = engagementDao.findAll(fine, start, coarse.next(start))
                    val deleted = engagementDao.deleteAll(fine, start, coarse.next(start))
                    // Otherwise another instance is consolidating them: counting them here too would double them
                    check(deleted == buckets.size) { "$deleted buckets deleted, ${buckets.size} read" }
                    buckets.groupBy { it.linkId }.forEach { (linkId, of) ->
                        engagementDao.add(EngagementBucket(linkId, of.first().host, coarse, start, of.sumOf { it.count }))
                    }
                }
            } catch (e: Exception) {
                logger.warn("Could not consolidate the visits of {} per {}: {}", start, coarse.name.lowercase(), e.message)
            }
        }
    }

    /**
     * Visits of [linkId], or of the links of [domainId], or of every domain the caller reads.
     */
    fun report(caller: LikaPrincipal, domainId: String?, linkId: String?, range: EngagementRange, now: Instant = Instant.now()): EngagementReport {
        flush()
        val from = range.from(now)
        val trackedSince = engagementDao.trackedSince()
        if (!linkId.isNullOrBlank()) {
            val link = deeplinkService.findOne(caller, linkId)
            val series = series(range, from, engagementDao.sumByPeriodForLink(link.id, from))
            return EngagementReport(range, range.resolution, from, now, series.sumOf { it.count }, series, link = link, trackedSince = trackedSince)
        }
        val domains = readableDomains(caller, domainId)
        val hosts = domains.map { it.host }
        val series = series(range, from, engagementDao.sumByPeriod(hosts, from))
        val byHost = engagementDao.sumByHost(hosts, from)
        // Deleted links keep counting for their domain, but are not listed. More are asked for, to make up for them.
        val links = engagementDao.topLinks(hosts, from, TOP_LINKS * 3)
            .mapNotNull { (id, count) -> deeplinkDao.findOne(id)?.takeIf { it.host in hosts }?.let { LinkEngagement(it, count) } }
            .take(TOP_LINKS)
        return EngagementReport(
            range, range.resolution, from, now, series.sumOf { it.count }, series,
            domains = domains.map { DomainEngagement(it.id!!, it.host, byHost[it.host] ?: 0) }.sortedWith(compareByDescending<DomainEngagement> { it.count }.thenBy { it.host }),
            links = links,
            trackedSince = trackedSince
        )
    }

    /** One point per period of the range, whatever the resolution the visits are kept at */
    private fun series(range: EngagementRange, from: Instant, periods: List<EngagementPeriod>): List<EngagementPoint> {
        val counts = TreeMap<Instant, Long>()
        var start = from
        repeat(range.periods.toInt()) {
            counts[start] = 0
            start = range.resolution.next(start)
        }
        periods.forEach { counts.computeIfPresent(range.resolution.startOf(it.start)) { _, count -> count + it.count } }
        return counts.map { EngagementPoint(it.key, it.value) }
    }

    /**
     * Links created over a year ago which nobody visited for a year, oldest first, among the links of [domainId]
     * or of every domain the caller reads. While Lika has counted visits for less than a year, links without
     * visit since it started are listed: [UnusedLinks.since] tells which.
     */
    fun unusedLinks(caller: LikaPrincipal, domainId: String?, now: Instant = Instant.now()): UnusedLinks {
        flush()
        val hosts = readableDomains(caller, domainId).map { it.host }
        val yearAgo = now.atZone(ZoneOffset.UTC).minusYears(1).toInstant()
        val trackedSince = engagementDao.trackedSince() ?: now
        val visited = engagementDao.visitedLinkIds(hosts)
        val unused = hosts.flatMap { deeplinkDao.findAllForHost(it) }
            .filter { it.createdDateTime.toInstant().isBefore(yearAgo) && it.id !in visited }
            .sortedBy { it.createdDateTime }
        return UnusedLinks(maxOf(yearAgo, trackedSince), trackedSince, unused.size, unused.take(MAX_UNUSED_LINKS))
    }

    private fun readableDomains(caller: LikaPrincipal, domainId: String?): List<Domain> =
        if (domainId.isNullOrBlank()) authorization.accessibleDomains(caller) else listOf(authorization.assertCan(caller, domainId, DomainRole.VIEWER))
}
