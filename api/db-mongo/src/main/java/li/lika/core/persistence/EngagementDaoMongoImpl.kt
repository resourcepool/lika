package li.lika.core.persistence

import jakarta.annotation.PostConstruct
import li.lika.common.models.EngagementBucket
import li.lika.common.models.EngagementPeriod
import li.lika.common.models.EngagementResolution
import li.lika.common.persistence.EngagementDao
import li.lika.core.persistence.models.EngagementDb
import org.bson.Document
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation.group
import org.springframework.data.mongodb.core.aggregation.Aggregation.limit
import org.springframework.data.mongodb.core.aggregation.Aggregation.match
import org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation
import org.springframework.data.mongodb.core.aggregation.Aggregation.sort
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Date

@Repository
class EngagementDaoMongoImpl(private val template: MongoTemplate) : EngagementDao {

    companion object {
        const val LINK = "linkId"
        const val HOST = "host"
        const val RESOLUTION = "resolution"
        const val START = "periodStart"
        const val VISITS = "visits"
    }

    @PostConstruct
    fun indexes() {
        val ops = template.indexOps(EngagementDb::class.java)
        ops.ensureIndex(Index().on(HOST, Sort.Direction.ASC).on(START, Sort.Direction.ASC))
        ops.ensureIndex(Index().on(LINK, Sort.Direction.ASC).on(START, Sort.Direction.ASC))
        ops.ensureIndex(Index().on(RESOLUTION, Sort.Direction.ASC).on(START, Sort.Direction.ASC))
    }

    override fun add(bucket: EngagementBucket) {
        template.upsert(
            query(where("_id").`is`(EngagementDb.idOf(bucket.linkId, bucket.resolution, bucket.start))),
            Update().inc(VISITS, bucket.count)
                .setOnInsert(LINK, bucket.linkId)
                .setOnInsert(HOST, bucket.host)
                .setOnInsert(RESOLUTION, bucket.resolution.code)
                .setOnInsert(START, bucket.start),
            EngagementDb::class.java
        )
    }

    override fun sumByPeriod(hosts: Collection<String>, from: Instant): List<EngagementPeriod> = sumByPeriod(inHosts(hosts).and(START).gte(from))

    override fun sumByPeriodForLink(linkId: String, from: Instant): List<EngagementPeriod> = sumByPeriod(where(LINK).`is`(linkId).and(START).gte(from))

    private fun sumByPeriod(criteria: Criteria): List<EngagementPeriod> {
        val aggregation = newAggregation(match(criteria), group(RESOLUTION, START).sum(VISITS).`as`(VISITS))
        return template.aggregate(aggregation, EngagementDb.COLLECTION, Document::class.java).mappedResults.map {
            val period = it["_id"] as Document
            EngagementPeriod(EngagementResolution.ofCode(period.getString(RESOLUTION)), period.getDate(START).toInstant(), (it[VISITS] as Number).toLong())
        }
    }

    override fun sumByHost(hosts: Collection<String>, from: Instant): Map<String, Long> {
        val aggregation = newAggregation(match(inHosts(hosts).and(START).gte(from)), group(HOST).sum(VISITS).`as`(VISITS))
        return template.aggregate(aggregation, EngagementDb.COLLECTION, Document::class.java).mappedResults
            .associate { it.getString("_id") to (it[VISITS] as Number).toLong() }
    }

    override fun topLinks(hosts: Collection<String>, from: Instant, limit: Int): List<Pair<String, Long>> {
        val aggregation = newAggregation(
            match(inHosts(hosts).and(START).gte(from)),
            group(LINK).sum(VISITS).`as`(VISITS),
            sort(Sort.by(Sort.Order.desc(VISITS), Sort.Order.asc("_id"))),
            limit(limit.toLong())
        )
        return template.aggregate(aggregation, EngagementDb.COLLECTION, Document::class.java).mappedResults
            .map { it.getString("_id") to (it[VISITS] as Number).toLong() }
    }

    override fun visitedLinkIds(hosts: Collection<String>): Set<String> =
        template.findDistinct(query(inHosts(hosts)), LINK, EngagementDb::class.java, String::class.java).toSet()

    override fun periodStarts(resolution: EngagementResolution, before: Instant): List<Instant> =
        template.findDistinct(query(where(RESOLUTION).`is`(resolution.code).and(START).lt(before)), START, EngagementDb::class.java, Date::class.java)
            .map { it.toInstant() }

    override fun findAll(resolution: EngagementResolution, from: Instant, to: Instant): List<EngagementBucket> =
        template.find(query(between(resolution, from, to)), EngagementDb::class.java).map { it.toDomain() }

    override fun deleteAll(resolution: EngagementResolution, from: Instant, to: Instant): Int =
        template.remove(query(between(resolution, from, to)), EngagementDb::class.java).deletedCount.toInt()

    override fun deleteBefore(before: Instant) {
        template.remove(query(where(RESOLUTION).ne(EngagementDb.TRACKED_SINCE).and(START).lt(before)), EngagementDb::class.java)
    }

    override fun deleteAllForHost(host: String) {
        template.remove(query(where(HOST).`is`(host)), EngagementDb::class.java)
    }

    override fun trackedSince(): Instant? = template.findById(EngagementDb.TRACKED_SINCE_ID, EngagementDb::class.java)?.periodStart

    override fun markTrackedSince(instant: Instant) {
        template.save(EngagementDb(id = EngagementDb.TRACKED_SINCE_ID, resolution = EngagementDb.TRACKED_SINCE, periodStart = instant))
    }

    private fun inHosts(hosts: Collection<String>) = where(HOST).`in`(hosts)

    private fun between(resolution: EngagementResolution, from: Instant, to: Instant) =
        where(RESOLUTION).`is`(resolution.code).and(START).gte(from).lt(to)
}
