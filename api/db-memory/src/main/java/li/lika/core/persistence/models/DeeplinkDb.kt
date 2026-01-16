package li.lika.core.persistence.models

import com.fasterxml.jackson.databind.ObjectMapper
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
    name = "deeplinks",
    indexes = [
        Index(name = "deeplink_host", columnList = "host", unique = false),
        Index(name = "deeplink_ownerId", columnList = "ownerId", unique = false),
        Index(name = "deeplink_slug", columnList = "slug", unique = false),
        Index(name = "deeplink_createdDateTime", columnList = "createdDateTime", unique = false),
    ]
)
class DeeplinkDb() {
    constructor(
        id: String,
        host: String,
        ownerId: String,
        slug: String?,
        shortenedUrl: String,
        targetUrl: String,
        type: DeeplinkType,
        createdDateTime: LocalDateTime?,
        lastUpdateDateTime: LocalDateTime?,
        optionActivationDateTime: LocalDateTime?,
        optionActivationTargetUrlBeforeActivation: String?,
        optionExpiryDate: LocalDate?,
        optionLandingTemplateId: String?,
        optionPassword: String?,
        metadata: String?
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
        this.optionPassword = optionPassword
        this.searchString = "$slug $targetUrl $host"
        this.metadata = metadata
    }

    @Id
    private var id: String? = null
    private var host: String = ""
    private var ownerId: String = ""
    private var slug: String? = null
    private var shortenedUrl: String = ""
    private var targetUrl: String = ""
    private var type: DeeplinkType = DeeplinkType.SIMPLE
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

    // Password options
    var optionPassword: String? = null
    var metadata: String? = null

    private var searchString: String = ""


    fun toDomain(mapper: ObjectMapper): Deeplink {
        return Deeplink(
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
                },
                password = when (optionPassword) {
                    null -> null
                    else -> PasswordProtectedOptions(optionPassword)
                }
            ),
            metadata = if (metadata.isNullOrBlank()) emptyMap() else mapper.readerForMapOf(String::class.java).readValue(metadata)
        )
    }

}

fun Deeplink.toDb(mapper: ObjectMapper): DeeplinkDb {
    return DeeplinkDb(
        id,
        host,
        ownerId,
        slug,
        shortenedUrl,
        targetUrl!!,
        type,
        createdDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
        lastUpdateDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
        options.activation?.activationInstant?.withZoneSameInstant(ZoneOffset.UTC)?.toLocalDateTime(),
        options.activation?.targetUrlBeforeActivation,
        options.expiry?.expiryDate,
        options.landing?.templateId,
        options.password?.password,
        if (metadata.isEmpty()) { null } else mapper.writeValueAsString(metadata)
    )
}

