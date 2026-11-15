package li.lika.common.models

/**
 * Credentials of Lika < 0.7.0, before users existed. Only read to migrate them to API keys.
 */
data class Account(val id: String?,
                   val name: String = "",
                   val global: Boolean = false,
                   val defaultAccessToken: String,
                   val defaultPublicReadAccessToken: String,
                   val domainIds: Set<String> = emptySet()) {
}
