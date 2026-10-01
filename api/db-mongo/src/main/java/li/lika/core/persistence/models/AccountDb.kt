package li.lika.core.persistence.models

import li.lika.common.models.Account
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "accounts")
data class AccountDb(
        @Id
        val id: String = "",
        val name: String = "",
        val global: Boolean = false,
        @Indexed
        val defaultAccessToken: String,
        @Indexed
        val defaultPublicReadAccessToken: String,
        val domainIds: Set<String>? = null,
        // Null for accounts created before domain grants existed
        val grantsVersion: Int? = null) {


    fun toDomain(): Account {
        return Account(id, name, global, defaultAccessToken, defaultPublicReadAccessToken, domainIds ?: emptySet())
    }

    companion object {
        const val CURRENT_GRANTS_VERSION = 1
    }
}

fun Account.toDb(): AccountDb {
    return AccountDb(
        id!!,
        name,
        global,
        defaultAccessToken,
        defaultPublicReadAccessToken,
        domainIds,
        AccountDb.CURRENT_GRANTS_VERSION)
}
