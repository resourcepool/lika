package li.lika.core.persistence.models

import li.lika.common.models.Domain
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "domains")
data class DomainDb(
        @Id
        var id: String = "",
        var standard: Boolean = false,
        @Indexed
        var ownerId: String? = null,
        @Indexed
        var host: String = "",
        var apiBaseUrl: String = "",
        var landingBaseUrl: String? = null
) {
    fun toDomain(): Domain {
        return Domain(id, standard, ownerId, host, apiBaseUrl, landingBaseUrl)
    }


    companion object {
        fun from(h: Domain): DomainDb {
            return DomainDb(h.id!!, h.standard, h.ownerId, h.host, h.apiBaseUrl, h.landingBaseUrl)
        }
    }
}

