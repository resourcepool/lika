package li.lika.core.persistence

import li.lika.common.models.EngagementBucket
import li.lika.common.models.EngagementPeriod
import li.lika.common.models.EngagementResolution
import li.lika.common.persistence.EngagementDao
import li.lika.core.persistence.models.EngagementDb
import li.lika.core.persistence.models.toDb
import li.lika.core.persistence.models.toInstantUtc
import li.lika.core.persistence.models.toUtc
import li.lika.core.persistence.repository.EngagementRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

@Service
class EngagementDaoJpaImpl(private val repository: EngagementRepository) : EngagementDao {

    override fun add(bucket: EngagementBucket) {
        val db = bucket.toDb()
        if (repository.increment(db.id, bucket.count) > 0) return
        try {
            repository.save(db)
        } catch (e: DataIntegrityViolationException) {
            // Created meanwhile by another instance
            repository.increment(db.id, bucket.count)
        }
    }

    override fun sumByPeriod(hosts: Collection<String>, from: Instant): List<EngagementPeriod> =
        if (hosts.isEmpty()) emptyList() else repository.sumByPeriod(hosts, from.toUtc()).map(::period)

    override fun sumByPeriodForLink(linkId: String, from: Instant): List<EngagementPeriod> =
        repository.sumByPeriodForLink(linkId, from.toUtc()).map(::period)

    override fun sumByHost(hosts: Collection<String>, from: Instant): Map<String, Long> =
        if (hosts.isEmpty()) emptyMap() else repository.sumByHost(hosts, from.toUtc()).associate { it[0] as String to (it[1] as Number).toLong() }

    override fun topLinks(hosts: Collection<String>, from: Instant, limit: Int): List<Pair<String, Long>> =
        if (hosts.isEmpty()) emptyList() else repository.topLinks(hosts, from.toUtc(), PageRequest.of(0, limit)).map { it[0] as String to (it[1] as Number).toLong() }

    override fun visitedLinkIds(hosts: Collection<String>): Set<String> =
        if (hosts.isEmpty()) emptySet() else repository.visitedLinkIds(hosts).toSet()

    override fun periodStarts(resolution: EngagementResolution, before: Instant): List<Instant> =
        repository.periodStarts(resolution.code, before.toUtc()).map { it.toInstantUtc() }

    override fun findAll(resolution: EngagementResolution, from: Instant, to: Instant): List<EngagementBucket> =
        repository.findBuckets(resolution.code, from.toUtc(), to.toUtc()).map { it.toDomain() }

    override fun deleteAll(resolution: EngagementResolution, from: Instant, to: Instant): Int =
        repository.deleteBuckets(resolution.code, from.toUtc(), to.toUtc())

    override fun deleteBefore(before: Instant) {
        repository.deleteBefore(before.toUtc(), EngagementDb.TRACKED_SINCE)
    }

    override fun deleteAllForHost(host: String) {
        repository.deleteAllForHost(host)
    }

    override fun trackedSince(): Instant? = repository.findById(EngagementDb.TRACKED_SINCE_ID).getOrNull()?.periodStart?.toInstantUtc()

    override fun markTrackedSince(instant: Instant) {
        repository.save(EngagementDb().also {
            it.id = EngagementDb.TRACKED_SINCE_ID
            it.resolution = EngagementDb.TRACKED_SINCE
            it.periodStart = instant.toUtc()
        })
    }

    private fun period(row: Array<Any>) =
        EngagementPeriod(EngagementResolution.ofCode(row[0] as String), (row[1] as LocalDateTime).toInstantUtc(), (row[2] as Number).toLong())
}
