package li.lika.common.models

data class PermalinkCreator(
        var id: String? = null,
        var targetUrl: String = "",
        var domainId: String = "",
        var slug: String? = null,
        var type: PermalinkType = PermalinkType.SIMPLE,
        var template: PermalinkTemplate? = null,
        var customTemplate: String? = null,
        var activationDateTime: String? = null,
        var expiryDate: String? = null)
