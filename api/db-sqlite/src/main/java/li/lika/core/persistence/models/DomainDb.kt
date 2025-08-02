package li.lika.core.persistence.models

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import li.lika.common.models.Domain

@Entity
@Table(name = "domains", indexes = [
    Index(name="owner", columnList = "ownerId", unique = true),
    Index(name="host", columnList = "host", unique = true),
])
class DomainDb() {

    @Id
    private var id: String? = null
    private var standard: Boolean = false
    private var ownerId: String? = null
    private var host: String = ""
    private var apiBaseUrl: String = ""
    private var landingBaseUrl: String? = null

    constructor(
        id: String,
        standard: Boolean,
        ownerId: String?,
        host: String,
        apiBaseUrl: String,
        landingBaseUrl: String?
    ): this() {
        this.id = id
        this.standard = standard
        this.ownerId = ownerId
        this.host = host
        this.apiBaseUrl = apiBaseUrl
        this.landingBaseUrl = landingBaseUrl
    }

    fun toDomain(): Domain {
        return Domain(id, standard, ownerId, host, apiBaseUrl, landingBaseUrl)
    }

}

fun Domain.toDb(): DomainDb {
    return DomainDb(id!!, standard, ownerId, host, apiBaseUrl, landingBaseUrl)
}

