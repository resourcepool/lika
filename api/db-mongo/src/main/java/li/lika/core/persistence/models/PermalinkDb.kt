package li.lika.core.persistence.models

import li.lika.common.models.*
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Document(collection = "permalinks")
data class PermalinkDb(
    @Id
    var id: String = "",
    @Indexed
    var host: String = "",
    @Indexed
    var ownerId: String = "",
    @Indexed
    var slug: String? = null,
    var shortenedUrl: String = "",
    var targetUrl: String = "",
    var type: PermalinkType = PermalinkType.SIMPLE,
    @Indexed
    var createdDateTime: LocalDateTime = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
    var lastUpdateDateTime: LocalDateTime = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),

    // Activation options
    var optionActivationDateTime: LocalDateTime? = null,
    var optionActivationTargetUrlBeforeActivation: String? = null,
    // Expiry options
    var optionExpiryDate: LocalDate? = null,
    // Landing options
    var optionLandingTemplateId: String? = null
) {

    fun toPermalink(): Permalink {
        return Permalink(
            id, host, ownerId, slug, shortenedUrl, targetUrl, type,
            createdDateTime.atZone(ZoneOffset.UTC),
            lastUpdateDateTime.atZone(ZoneOffset.UTC),
            options = Options(
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

    companion object {
        fun from(pl: Permalink): PermalinkDb {
            return PermalinkDb(
                pl.id,
                pl.host,
                pl.ownerId,
                pl.slug,
                pl.shortenedUrl,
                pl.targetUrl,
                pl.type,
                pl.createdDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                pl.lastUpdateDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                pl.options.activation?.activationInstant?.withZoneSameInstant(ZoneOffset.UTC)?.toLocalDateTime(),
                pl.options.activation?.targetUrlBeforeActivation,
                pl.options.expiry?.expiryDate,
                pl.options.landing?.templateId
            )
        }
    }
}

