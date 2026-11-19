package li.lika.common.persistence

import li.lika.common.models.EngagementBucket
import li.lika.common.models.EngagementPeriod
import li.lika.common.models.EngagementResolution
import java.time.Instant

/**
 * Visit counts of links. One bucket per link and period; periods never overlap, so sums count each visit once.
 */
interface EngagementDao {
    /** Adds the visits of [bucket] to the bucket of the same link and period, created when missing */
    fun add(bucket: EngagementBucket)

    /** Visits per period of the links of [hosts], for the periods starting at or after [from] */
    fun sumByPeriod(hosts: Collection<String>, from: Instant): List<EngagementPeriod>

    fun sumByPeriodForLink(linkId: String, from: Instant): List<EngagementPeriod>

    /** Visits per host since [from] */
    fun sumByHost(hosts: Collection<String>, from: Instant): Map<String, Long>

    /** The [limit] most visited links of [hosts] since [from], most visited first: link id and visits */
    fun topLinks(hosts: Collection<String>, from: Instant, limit: Int): List<Pair<String, Long>>

    /** Ids of the links of [hosts] with at least one visit kept */
    fun visitedLinkIds(hosts: Collection<String>): Set<String>

    /** Distinct starts of the periods of [resolution] starting before [before] */
    fun periodStarts(resolution: EngagementResolution, before: Instant): List<Instant>

    /** Buckets of [resolution] starting in [from, to) */
    fun findAll(resolution: EngagementResolution, from: Instant, to: Instant): List<EngagementBucket>

    /** @return how many buckets were deleted */
    fun deleteAll(resolution: EngagementResolution, from: Instant, to: Instant): Int

    /** Deletes the buckets starting before [before], whatever their resolution */
    fun deleteBefore(before: Instant)

    fun deleteAllForHost(host: String)

    /** When Lika started counting visits, null until [markTrackedSince] */
    fun trackedSince(): Instant?

    fun markTrackedSince(instant: Instant)
}
