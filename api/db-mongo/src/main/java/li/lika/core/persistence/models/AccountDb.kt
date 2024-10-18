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
        val defaultPublicReadAccessToken: String) {


    fun toDomain(): Account {
        return Account(id, name, global, defaultAccessToken, defaultPublicReadAccessToken)
    }
}

fun Account.toDb(): AccountDb {
    return AccountDb(
        id!!,
        name,
        global,
        defaultAccessToken,
        defaultPublicReadAccessToken)
}
