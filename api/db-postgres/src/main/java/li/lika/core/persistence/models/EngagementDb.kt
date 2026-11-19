package li.lika.core.persistence.models

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import li.lika.common.models.EngagementBucket
import li.lika.common.models.EngagementResolution
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Visits of a link during a period. The id is made of the link, the resolution and the start of the period,
 * so that counting a visit is one update.
 */
@Entity
@Table(
    name = "engagement",
    indexes = [
        Index(name = "engagement_host_start", columnList = "host, periodStart", unique = false),
        Index(name = "engagement_link_start", columnList = "linkId, periodStart", unique = false),
        Index(name = "engagement_resolution_start", columnList = "resolution, periodStart", unique = false)
    ]
)
class EngagementDb() {
    @Id
    var id: String = ""
    var linkId: String = ""
    var host: String = ""
    /** Code of the [EngagementResolution], or [TRACKED_SINCE] */
    var resolution: String = ""
    var periodStart: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
    var visits: Long = 0

    fun toDomain(): EngagementBucket = EngagementBucket(linkId, host, EngagementResolution.ofCode(resolution), periodStart.toInstantUtc(), visits)

    companion object {
        /** Resolution and id of the row telling when Lika started counting visits */
        const val TRACKED_SINCE = "-"
        const val TRACKED_SINCE_ID = "tracked-since"

        fun idOf(linkId: String, resolution: EngagementResolution, start: java.time.Instant) = "$linkId:${resolution.code}:${start.epochSecond}"
    }
}

fun EngagementBucket.toDb(): EngagementDb = EngagementDb().also {
    it.id = EngagementDb.idOf(linkId, resolution, start)
    it.linkId = linkId
    it.host = host
    it.resolution = resolution.code
    it.periodStart = start.toUtc()
    it.visits = count
}
