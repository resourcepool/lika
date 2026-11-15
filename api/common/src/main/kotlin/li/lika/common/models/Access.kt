package li.lika.common.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * What a user or an API key may do on one domain. Each role includes the previous ones.
 */
enum class DomainRole(val value: String) {
    /** Reads and searches the links of the domain */
    @JsonProperty("viewer") VIEWER("viewer"),
    /** Creates, edits and deletes the links of the domain */
    @JsonProperty("editor") EDITOR("editor"),
    /** Also invites users to the domain, sets their role on it, and manages its API keys. Users only. */
    @JsonProperty("manager") MANAGER("manager");

    fun includes(other: DomainRole): Boolean = ordinal >= other.ordinal

    companion object {
        fun parse(value: String): DomainRole =
            entries.firstOrNull { it.value == value.lowercase() } ?: throw IllegalArgumentException("Unknown domain role: $value")
    }
}

/**
 * Rights carried by the `lika` claim of a user token, whatever the identity provider.
 * API keys get the same shape, built from the key itself.
 *
 * @param v version of the user's rights and sessions: a token carrying an older version is refused
 * @param admin every right on every domain, plus domains, users, keys and archives
 * @param domains role per domain id
 */
data class LikaClaims(
    val v: Long = 0,
    val admin: Boolean = false,
    val domains: Map<String, DomainRole> = emptyMap()
) {
    /** Role on the domain, admins being managers of every domain */
    fun roleOn(domainId: String): DomainRole? = if (admin) DomainRole.MANAGER else domains[domainId]

    fun can(domainId: String, role: DomainRole): Boolean = roleOn(domainId)?.includes(role) == true

    companion object {
        const val CLAIM_NAME = "lika"
    }
}
