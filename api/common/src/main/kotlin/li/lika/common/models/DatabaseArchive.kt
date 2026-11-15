package li.lika.common.models

data class DeeplinkArchiveItem(
    var targetUrl: String = "",
    var slug: String? = null,
    var type: DeeplinkType = DeeplinkType.SIMPLE,
    var options: OptionsDto = OptionsDto(),
    // Since v2
    var metadata: Map<String, String> = emptyMap(),
    var passwordHash: String? = null,
    /** v2: account name. Since v3: email of the user, or name of the API key */
    var createdBy: String? = null,
    var createdDateTime: String? = null,
    var lastUpdateDateTime: String? = null
)

data class DomainArchiveItem(
    var standard: Boolean = false,
    var host: String = "",
    var apiBaseUrl: String = "",
    var landingBaseUrl: String? = null,
    var links: List<DeeplinkArchiveItem> = emptyList(),
    // v2 only: name of the owning account, null for system domains
    var owner: String? = null
) {
    companion object

}

/** v1 and v2 only: accounts became API keys in v3 */
data class AccountArchiveItem(
    var name: String = "",
    var root: Boolean = false,
    var global: Boolean = false,
    var secretToken: String? = null,
    var publicReadOnlyToken: String? = null,
    // v1 only: domains nested under their owner. Always empty in v2 exports.
    var domains: List<DomainArchiveItem> = emptyList(),
    // Since v2: hosts of the domains granted to this account
    var grants: List<String> = emptyList()
) {
    companion object
}

data class UserArchiveItem(
    var email: String = "",
    var name: String = "",
    var status: UserStatus = UserStatus.ACTIVE,
    var admin: Boolean = false,
    /** Role per domain host */
    var domains: Map<String, DomainRole> = emptyMap(),
    var passwordHash: String? = null,
    var createdAt: String? = null
)

data class ApiKeyArchiveItem(
    var name: String = "",
    var admin: Boolean = false,
    /** Role per domain host */
    var domains: Map<String, DomainRole> = emptyMap(),
    var secretHash: String = "",
    var prefix: String = "",
    var createdAt: String? = null,
    var expiresAt: String? = null,
    /** Email of the owner of a user API key */
    var user: String? = null
)

data class DatabaseArchive(
    // v1 and v2 only
    var accounts: List<AccountArchiveItem> = emptyList(),
    // Since v2
    var version: Int = 1,
    var domains: List<DomainArchiveItem> = emptyList(),
    // Since v3
    var users: List<UserArchiveItem> = emptyList(),
    var apiKeys: List<ApiKeyArchiveItem> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 3
    }
}

enum class ImportConflictMode {
    @com.fasterxml.jackson.annotation.JsonProperty("skip") SKIP,
    @com.fasterxml.jackson.annotation.JsonProperty("overwrite") OVERWRITE
}

data class ImportCounts(var created: Int = 0, var updated: Int = 0, var skipped: Int = 0)

data class ImportReport(
    val dryRun: Boolean,
    val conflictMode: ImportConflictMode,
    val users: ImportCounts = ImportCounts(),
    val apiKeys: ImportCounts = ImportCounts(),
    val domains: ImportCounts = ImportCounts(),
    val links: ImportCounts = ImportCounts()
)
