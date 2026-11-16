package li.lika.core.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import li.lika.common.models.Deeplink
import li.lika.common.models.dtos.PageRequest
import li.lika.common.models.dtos.PageResponse
import li.lika.common.persistence.DeeplinkDao
import li.lika.core.persistence.models.toDb
import li.lika.core.persistence.repository.PermalinkRepository
import li.lika.core.persistence.repository.utils.DeeplinkSpecifications
import li.lika.core.persistence.repository.utils.toPageResponse
import li.lika.core.persistence.repository.utils.toSpringSort
import mu.KotlinLogging
import org.springframework.stereotype.Repository
import java.io.IOException
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull
import org.springframework.data.domain.PageRequest as SpringPageRequest

@Repository
class DeeplinkDaoSQLiteImpl(private val repository: PermalinkRepository, private val mapper: ObjectMapper) : DeeplinkDao {
    private val logger = KotlinLogging.logger { }

    override fun findOne(id: String): Deeplink? {
        val r = repository.findById(id)
        return r.getOrNull()?.toDomain(mapper)
    }

    override fun findByHostAndSlug(host: String, slug: String): Deeplink? {
        val r = repository.findFirstByHostAndSlug(host, slug).getOrNull()
        return r?.toDomain(mapper)
    }

    override fun save(p: Deeplink): Deeplink {
        return repository.save(p.toDb(mapper)).toDomain(mapper)
    }

    override fun findAll(): List<Deeplink> {
        return repository.findAll()
            .map { db -> db.toDomain(mapper) }
    }

    override fun findAllForHost(host: String): List<Deeplink> {
        return repository.findAllByHostOrderByLastUpdateDateTimeDesc(host)
                .map { db -> db.toDomain(mapper) }
    }

    override fun countForHost(host: String): Int {
        return repository.countByHost(host).toInt()
    }

    override fun findPageForHosts(hosts: Collection<String>, req: PageRequest): PageResponse<Deeplink> {
        val pageRequest = SpringPageRequest.of(req.offset / req.limit, req.limit, req.sort!!.toSpringSort())
        if (hosts.isEmpty()) {
            return PageResponse(req, 0, emptyList())
        }
        val results = if (req.filter.isNullOrBlank() && req.metadata.isEmpty()) {
            repository.findAllByHostIn(hosts, pageRequest)
        } else {
            repository.findAll(DeeplinkSpecifications.matching(hosts, req, mapper), pageRequest)
        }
        return results.toPageResponse(req) { db -> db.toDomain(mapper) }
    }

    override fun removeAllExpired(): List<Deeplink> {
        return repository.deleteAllByOptionExpiryDateIsBefore(LocalDate.now().minusDays(1))
            .map { it.toDomain(mapper) }
    }

    override fun deleteBySlug(host: String, slug: String) {
        try {
            repository.deleteAllByHostAndSlug(host, slug)
        } catch (e: IOException) {
            logger.error("Error occurred during deeplink deletion: {}", e.message)
            throw IllegalStateException(e)
        }
    }

    override fun deleteAllForHost(host: String) {
        repository.deleteAllByHost(host)
    }

    override fun deleteOne(id: String) {
        repository.deleteById(id)
    }

    /**
     * Warning! This is voluntarily synchronous
     */
    override fun deleteAll() {
        try {
            repository.deleteAll()
        } catch (e: IOException) {
            logger.error("Error occurred during deeplink deletion: {}", e.message)
            throw IllegalStateException(e)
        }
    }



}
