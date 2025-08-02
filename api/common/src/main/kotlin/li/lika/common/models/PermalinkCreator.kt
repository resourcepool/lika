package li.lika.common.models


data class ActivationOptionsDto(
        var activationInstant: String? = null,
        var targetUrlBeforeActivation: String? = null)

data class LandingOptionsDto(
        var templateId: String? = null
)

data class ExpiryOptionsDto(
        var expiryDate: String? = null
)

data class OptionsDto(
        var activation: ActivationOptionsDto? = null,
        var landing: LandingOptionsDto? = null,
        var expiry: ExpiryOptionsDto? = null)


data class PermalinkCreator(
        var id: String? = null,
        var targetUrl: String = "",
        var domainId: String = "",
        var slug: String? = null,
        var type: PermalinkType = PermalinkType.SIMPLE,
        var options: OptionsDto = OptionsDto())
