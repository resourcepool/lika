package li.lika.common.models

data class Account(val id: String?,
                   val name: String = "",
                   val global: Boolean = false,
                   val defaultAccessToken: String,
                   val defaultPublicReadAccessToken: String,
                   /** Domains this account may manage, in addition to the ones it owns. Ignored for global accounts. */
                   val domainIds: Set<String> = emptySet()) {
}
