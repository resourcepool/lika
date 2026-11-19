package li.lika.core.persistence.models

import li.lika.common.models.EngagementBucket
import li.lika.common.models.EngagementResolution
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Visits of a link during a period. The id is made of the link, the resolution and the start of the period,
 * so that counting a visit is one upsert.
 */
@Document(collection = EngagementDb.COLLECTION)
data class EngagementDb(
    @Id val id: String = "",
    val linkId: String = "",
    val host: String = "",
    /** Code of the [EngagementResolution], or [TRACKED_SINCE] */
    val resolution: String = "",
    val periodStart: Instant = Instant.now(),
    val visits: Long = 0
) {
    fun toDomain() = EngagementBucket(linkId, host, EngagementResolution.ofCode(resolution), periodStart, visits)

    companion object {
        const val COLLECTION = "engagement"
        /** Resolution and id of the document telling when Lika started counting visits */
        const val TRACKED_SINCE = "-"
        const val TRACKED_SINCE_ID = "tracked-since"

        fun idOf(linkId: String, resolution: EngagementResolution, start: Instant) = "$linkId:${resolution.code}:${start.epochSecond}"
    }
}
