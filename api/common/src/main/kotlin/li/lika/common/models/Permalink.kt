package li.lika.common.models

import java.time.LocalDate
import java.time.ZonedDateTime

data class ActivationOptions(
        var activationInstant: ZonedDateTime? = null,
        var targetUrlBeforeActivation: String? = null)

data class LandingOptions(
        var templateId: String? = null
)

data class ExpiryOptions(
        var expiryDate: LocalDate? = null
)

data class Options(
        var activation: ActivationOptions? = null,
        var landing: LandingOptions? = null,
        var expiry: ExpiryOptions? = null)

data class Permalink(
        var id: String = "",
        var host: String = "",
        var ownerId: String = "",
        var slug: String? = null,
        var shortenedUrl: String = "",
        var targetUrl: String = "",
        var type: PermalinkType = PermalinkType.SIMPLE,
        var createdDateTime: ZonedDateTime = ZonedDateTime.now(),
        var lastUpdateDateTime: ZonedDateTime = ZonedDateTime.now(),
        var options: Options = Options()
)
