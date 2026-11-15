package li.lika.core.persistence.models

import li.lika.common.models.Account
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

/** Accounts of Lika < 0.7.0, read once by the migration to API keys */
@Document(collection = "accounts")
data class AccountDb(
        @Id
        val id: String = "",
        val name: String = "",
        val global: Boolean = false,
        val defaultAccessToken: String = "",
        val defaultPublicReadAccessToken: String = "",
        val domainIds: Set<String>? = null) {

    fun toDomain(): Account {
        return Account(id, name, global, defaultAccessToken, defaultPublicReadAccessToken, domainIds ?: emptySet())
    }
}
