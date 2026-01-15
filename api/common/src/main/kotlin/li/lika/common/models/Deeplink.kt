package li.lika.common.models

import java.time.LocalDate
import java.time.ZonedDateTime

data class ActivationOptions(
    var activationInstant: ZonedDateTime? = null,
    var targetUrlBeforeActivation: String? = null
)

data class LandingOptions(
    var templateId: String? = null
)

data class ExpiryOptions(
    var expiryDate: LocalDate? = null
)

data class PasswordProtectedOptions(
    var password: String? = null
)

data class Options(
    var activation: ActivationOptions? = null,
    var landing: LandingOptions? = null,
    var expiry: ExpiryOptions? = null,
    var password: PasswordProtectedOptions? = null
)

data class Deeplink(
    var id: String = "",
    var host: String = "",
    var ownerId: String = "",
    var slug: String? = null,
    var shortenedUrl: String = "",
    var targetUrl: String? = null,
    var type: DeeplinkType = DeeplinkType.SIMPLE,
    var createdDateTime: ZonedDateTime = ZonedDateTime.now(),
    var lastUpdateDateTime: ZonedDateTime = ZonedDateTime.now(),
    var options: Options = Options()
)

fun Deeplink.isPasswordProtected(): Boolean {
    return !(options.password?.password.isNullOrBlank())

}


fun Deeplink.hideTarget(): Deeplink {
    return this.copy(
        id = this.id,
        host = this.host,
        ownerId = this.ownerId,
        slug = this.slug,
        shortenedUrl = this.shortenedUrl,
        targetUrl = null, // should be null
        type = this.type,
        createdDateTime = this.createdDateTime,
        lastUpdateDateTime = this.lastUpdateDateTime,
        options = this.options.copy(
            activation = options.activation,
            landing = options.landing,
            expiry = options.expiry,
            password = null // should not send password
        ))
}


fun Deeplink.safe(): Deeplink {
    return this.copy(
        id = this.id,
        host = this.host,
        ownerId = this.ownerId,
        slug = this.slug,
        shortenedUrl = this.shortenedUrl,
        targetUrl = this.targetUrl, // should be null
        type = this.type,
        createdDateTime = this.createdDateTime,
        lastUpdateDateTime = this.lastUpdateDateTime,
        options = this.options.copy(
            activation = options.activation,
            landing = options.landing,
            expiry = options.expiry,
            password = null // should not send password
        ))
}
