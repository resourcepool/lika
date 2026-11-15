package li.lika.core.persistence.models

import jakarta.persistence.*
import li.lika.common.models.Account

/** Accounts of Lika < 0.7.0, read once by the migration to API keys */
@Entity
@Table(name = "accounts")
class AccountDb {
    @Id
    private var id: String? = null
    private var name: String = ""
    private var global: Boolean = false
    private var defaultAccessToken: String? = null
    private var defaultPublicReadAccessToken: String? = null

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_domains", joinColumns = [JoinColumn(name = "account_id")])
    @Column(name = "domain_id")
    private var domainIds: MutableSet<String> = mutableSetOf()

    fun toDomain(): Account {
        return Account(id, name, global, defaultAccessToken!!, defaultPublicReadAccessToken!!, domainIds.toSet())
    }
}
