package li.lika.common.models

import com.fasterxml.jackson.annotation.JsonIgnore
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
    /** BCrypt hash of the password. Never serialized. */
    @get:JsonIgnore
    var passwordHash: String? = null
) {
    val enabled: Boolean
        get() = !passwordHash.isNullOrBlank()
}

data class Options(
    var activation: ActivationOptions? = null,
    var landing: LandingOptions? = null,
    var expiry: ExpiryOptions? = null,
    var password: PasswordProtectedOptions? = null
)

data class Deeplink(
    var id: String = "",
    var host: String = "",
    /** Id of the account which created the link. Any account with access to the domain may manage it. */
    var createdBy: String = "",
    var slug: String? = null,
    var shortenedUrl: String = "",
    var targetUrl: String? = null,
    var type: DeeplinkType = DeeplinkType.SIMPLE,
    var createdDateTime: ZonedDateTime = ZonedDateTime.now(),
    var lastUpdateDateTime: ZonedDateTime = ZonedDateTime.now(),
    var options: Options = Options(),
    var metadata: Map<String, String> = HashMap()
)

fun Deeplink.isPasswordProtected(): Boolean {
    return options.password?.enabled == true
}

fun Deeplink.isExpired(today: LocalDate = LocalDate.now()): Boolean {
    val expiryDate = options.expiry?.expiryDate ?: return false
    return today.isAfter(expiryDate)
}

fun Deeplink.isActivated(now: ZonedDateTime = ZonedDateTime.now()): Boolean {
    val activationInstant = options.activation?.activationInstant ?: return true
    return !now.isBefore(activationInstant)
}

/**
 * The URL a visitor should be sent to right now, or null if the link should not resolve.
 */
fun Deeplink.currentTargetUrl(now: ZonedDateTime = ZonedDateTime.now()): String? {
    if (isExpired(now.toLocalDate())) {
        return null
    }
    if (!isActivated(now)) {
        return options.activation?.targetUrlBeforeActivation?.takeIf { it.isNotBlank() }
    }
    return targetUrl
}

fun Deeplink.hideTarget(): Deeplink {
    return this.copy(targetUrl = null)
}
