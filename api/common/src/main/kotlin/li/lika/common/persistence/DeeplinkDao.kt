package li.lika.common.persistence

import li.lika.common.models.Deeplink
import li.lika.common.models.dtos.PageRequest
import li.lika.common.models.dtos.PageResponse

interface DeeplinkDao {
    fun findOne(id: String): Deeplink?
    fun findByHostAndSlug(host: String, slug: String): Deeplink?
    fun save(p: Deeplink): Deeplink
    fun findAll(): List<Deeplink>
    fun findAllForHost(host: String): List<Deeplink>
    fun removeAllExpired(): List<Deeplink>
    fun deleteBySlug(host: String, slug: String)
    fun deleteOne(id: String)

    /**
     * Warning! This is voluntarily synchronous
     */
    fun deleteAll()
    fun findPageForHost(host: String, req: PageRequest): PageResponse<Deeplink>
}
