package li.lika.common.models


data class ActivationOptionsDto(
        var activationInstant: String? = null,
        var targetUrlBeforeActivation: String? = null)

data class LandingOptionsDto(
        var templateId: String? = null
)

/**
 * On edit, omitting the password options keeps the current password.
 * Set [password] to replace it, or [remove] to drop the protection.
 */
data class PasswordProtectedOptionsDto(
    var password: String? = null,
    var remove: Boolean = false
)

data class ExpiryOptionsDto(
        var expiryDate: String? = null
)

data class OptionsDto(
        var activation: ActivationOptionsDto? = null,
        var landing: LandingOptionsDto? = null,
        var expiry: ExpiryOptionsDto? = null,
        var password: PasswordProtectedOptionsDto? = null)


data class DeeplinkCreator(
    var id: String? = null,
    var targetUrl: String = "",
    var domainId: String = "",
    var slug: String? = null,
    var type: DeeplinkType = DeeplinkType.SIMPLE,
    var options: OptionsDto = OptionsDto(),
    var metadata: Map<String, String> = HashMap()
)
