package li.lika.common.models

import java.time.LocalDate
import java.time.ZonedDateTime

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
        var activationDateTime: ZonedDateTime? = null,
        var expiryDate: LocalDate? = null,
        var template: PermalinkTemplate? = null,
        var customTemplate: String? = null)
