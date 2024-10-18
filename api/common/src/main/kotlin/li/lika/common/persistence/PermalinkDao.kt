package li.lika.common.persistence

import li.lika.common.models.Permalink
import li.lika.common.models.dtos.PageRequest
import li.lika.common.models.dtos.PageResponse

interface PermalinkDao {
    fun findOne(id: String): Permalink?
    fun findByHostAndSlug(host: String, slug: String): Permalink?
    fun save(p: Permalink): Permalink
    fun findAll(): List<Permalink>
    fun findAllForHost(host: String): List<Permalink>
    fun removeAllExpired(): List<Permalink>
    fun deleteBySlug(host: String, slug: String)
    fun deleteOne(id: String)

    /**
     * Warning! This is voluntarily synchronous
     */
    fun deleteAll()
    fun findPageForHost(host: String, req: PageRequest): PageResponse<Permalink>
}
