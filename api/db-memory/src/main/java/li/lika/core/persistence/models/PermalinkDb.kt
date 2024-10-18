package li.lika.core.persistence.models

import jakarta.persistence.*
import li.lika.common.models.Permalink
import li.lika.common.models.PermalinkTemplate
import li.lika.common.models.PermalinkType
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Entity
@Table(
    name = "permalinks",
    indexes = [
        Index(name = "permalink_host", columnList = "host", unique = false),
        Index(name = "permalink_ownerId", columnList = "ownerId", unique = false),
        Index(name = "permalink_slug", columnList = "slug", unique = false),
        Index(name = "permalink_createdDateTime", columnList = "createdDateTime", unique = false),
    ]
)
class PermalinkDb() {
    constructor(
        id: String,
        host: String,
        ownerId: String,
        slug: String?,
        shortenedUrl: String,
        targetUrl: String,
        type: PermalinkType,
        createdDateTime: LocalDateTime?,
        lastUpdateDateTime: LocalDateTime?,
        activationDateTime: LocalDateTime?,
        expiryDate: LocalDate?,
        template: PermalinkTemplate?,
        customTemplate: String?
    ): this() {
        this.id = id
        this.host = host
        this.ownerId = ownerId
        this.slug = slug
        this.shortenedUrl = shortenedUrl
        this.targetUrl = targetUrl
        this.type = type
        this.createdDateTime = createdDateTime!!
        this.lastUpdateDateTime = lastUpdateDateTime!!
        this.activationDateTime = activationDateTime
        this.expiryDate = expiryDate
        this.template = template
        this.customTemplate = customTemplate
        this.searchString = "$slug $targetUrl $host"
    }

    @Id
    private var id: String? = null
    private var host: String = ""
    private var ownerId: String = ""
    private var slug: String? = null
    private var shortenedUrl: String = ""
    private var targetUrl: String = ""
    private var type: PermalinkType = PermalinkType.SIMPLE
    private var createdDateTime: LocalDateTime = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
    private var lastUpdateDateTime: LocalDateTime = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
    private var activationDateTime: LocalDateTime? = null
    private var expiryDate: LocalDate? = null
    private var template: PermalinkTemplate? = null
    private var customTemplate: String? = null
    private var searchString: String = ""


    fun toDomain(): Permalink {
        return Permalink(
                id!!, host, ownerId, slug, shortenedUrl, targetUrl, type,
                createdDateTime.atZone(ZoneOffset.UTC),
                lastUpdateDateTime.atZone(ZoneOffset.UTC),
                activationDateTime?.atZone(ZoneOffset.UTC), expiryDate, template, customTemplate
        )
    }

}

fun Permalink.toDb(): PermalinkDb {
    return PermalinkDb(
        id,
        host,
        ownerId,
        slug,
        shortenedUrl,
        targetUrl,
        type,
        createdDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
        lastUpdateDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
        activationDateTime?.withZoneSameInstant(ZoneOffset.UTC)?.toLocalDateTime(),
        expiryDate,
        template,
        customTemplate
    )
}

