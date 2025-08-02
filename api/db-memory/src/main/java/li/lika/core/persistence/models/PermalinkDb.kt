package li.lika.core.persistence.models

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import li.lika.common.models.*
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
        optionActivationDateTime: LocalDateTime?,
        optionActivationTargetUrlBeforeActivation: String?,
        optionExpiryDate: LocalDate?,
        optionLandingTemplateId: String?,
    ) : this() {
        this.id = id
        this.host = host
        this.ownerId = ownerId
        this.slug = slug
        this.shortenedUrl = shortenedUrl
        this.targetUrl = targetUrl
        this.type = type
        this.createdDateTime = createdDateTime!!
        this.lastUpdateDateTime = lastUpdateDateTime!!
        this.optionActivationDateTime = optionActivationDateTime
        this.optionActivationTargetUrlBeforeActivation = optionActivationTargetUrlBeforeActivation
        this.optionExpiryDate = optionExpiryDate
        this.optionLandingTemplateId = optionLandingTemplateId
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
    private var createdDateTime: LocalDateTime =
        ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
    private var lastUpdateDateTime: LocalDateTime =
        ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()

    // Activation options
    var optionActivationDateTime: LocalDateTime? = null
    var optionActivationTargetUrlBeforeActivation: String? = null

    // Expiry options
    var optionExpiryDate: LocalDate? = null

    // Landing options
    var optionLandingTemplateId: String? = null

    private var searchString: String = ""


    fun toDomain(): Permalink {
        return Permalink(
            id!!, host, ownerId, slug, shortenedUrl, targetUrl, type,
            createdDateTime.atZone(ZoneOffset.UTC),
            lastUpdateDateTime.atZone(ZoneOffset.UTC),
            Options(
                activation = when (optionActivationDateTime?.atZone(ZoneOffset.UTC)) {
                    null -> null
                    else -> ActivationOptions(
                        optionActivationDateTime?.atZone(ZoneOffset.UTC),
                        optionActivationTargetUrlBeforeActivation
                    )
                },
                landing = when (optionLandingTemplateId) {
                    null -> null
                    else -> LandingOptions(optionLandingTemplateId)
                },
                expiry = when (optionExpiryDate) {
                    null -> null
                    else -> ExpiryOptions(optionExpiryDate)
                }
            )
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
        options.activation?.activationInstant?.withZoneSameInstant(ZoneOffset.UTC)?.toLocalDateTime(),
        options.activation?.targetUrlBeforeActivation,
        options.expiry?.expiryDate,
        options.landing?.templateId
    )
}

