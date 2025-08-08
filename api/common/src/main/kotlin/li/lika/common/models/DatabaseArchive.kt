package li.lika.common.models

data class PermalinkArchiveItem(
    var targetUrl: String = "",
    var slug: String? = null,
    var type: PermalinkType = PermalinkType.SIMPLE,
    var options: OptionsDto = OptionsDto()
)

data class DomainArchiveItem(
    var standard: Boolean = false,
    var host: String = "",
    var apiBaseUrl: String = "",
    var landingBaseUrl: String? = null,
    var links: List<PermalinkArchiveItem>
) {
    companion object

}

data class AccountArchiveItem(
    var name: String = "",
    var root: Boolean = false,
    var global: Boolean = false,
    var secretToken: String? = null,
    var publicReadOnlyToken: String? = null,
    var domains: List<DomainArchiveItem>
) {
    companion object
}

data class DatabaseArchive(
    var accounts: List<AccountArchiveItem>)
