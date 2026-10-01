package li.lika.core.persistence.models

import jakarta.persistence.*
import li.lika.common.models.Account
import org.hibernate.annotations.UuidGenerator

@Entity
@Table(
    name = "accounts",
    indexes = [
    Index(name = "default_access_token", columnList = "defaultAccessToken", unique = true),
    Index(name = "default_public_read_access_token", columnList = "defaultPublicReadAccessToken", unique = true)
])
class AccountDb() {
    constructor(
        id: String,
        name: String,
        global: Boolean,
        defaultAccessToken: String,
        defaultPublicReadAccessToken: String,
        domainIds: Set<String>,
    ): this() {
        this.id = id
        this.name = name
        this.global = global
        this.defaultAccessToken = defaultAccessToken
        this.defaultPublicReadAccessToken = defaultPublicReadAccessToken
        this.domainIds = domainIds.toMutableSet()
        this.grantsVersion = CURRENT_GRANTS_VERSION
    }

    @Id
    @UuidGenerator
    @GeneratedValue(strategy = GenerationType.UUID)
    private var id: String? = null
    private var name: String = ""
    private var global: Boolean = false
    private var defaultAccessToken: String? = null
    private var defaultPublicReadAccessToken: String? = null

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_domains", joinColumns = [JoinColumn(name = "account_id")])
    @Column(name = "domain_id")
    private var domainIds: MutableSet<String> = mutableSetOf()

    // Null for accounts created before domain grants existed
    private var grantsVersion: Int? = null

    fun toDomain(): Account {
        return Account(id, name, global, defaultAccessToken!!, defaultPublicReadAccessToken!!, domainIds.toSet())
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
        domainIds)
}
