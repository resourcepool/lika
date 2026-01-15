package li.lika.core.persistence

import li.lika.common.models.Deeplink
import li.lika.common.models.dtos.PageRequest
import li.lika.common.models.dtos.PageResponse
import li.lika.common.persistence.DeeplinkDao
import li.lika.core.persistence.models.DeeplinkDb
import li.lika.core.persistence.models.toDb
import li.lika.core.persistence.repository.PermalinkRepository
import li.lika.core.persistence.repository.utils.toPageResponse
import li.lika.core.persistence.repository.utils.toSpringSort
import mu.KotlinLogging
import org.springframework.stereotype.Repository
import java.io.IOException
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull
import org.springframework.data.domain.PageRequest as SpringPageRequest

@Repository
class DeeplinkDaoH2Impl(private val repository: PermalinkRepository) : DeeplinkDao {
    private val logger = KotlinLogging.logger { }

    override fun findOne(id: String): Deeplink? {
        val r = repository.findById(id)
        return r.getOrNull()?.toDomain()
    }

    override fun findByHostAndSlug(host: String, slug: String): Deeplink? {
        val r = repository.findFirstByHostAndSlug(host, slug).getOrNull()
        return r?.toDomain()
    }

    override fun save(p: Deeplink): Deeplink {
        return repository.save(p.toDb()).toDomain()
    }

    override fun findAll(): List<Deeplink> {
        return repository.findAll()
            .map(DeeplinkDb::toDomain)
    }

    override fun findAllForHost(host: String): List<Deeplink> {
        return repository.findAllByHostOrderByLastUpdateDateTimeDesc(host)
                .map(DeeplinkDb::toDomain)
    }

    override fun findPageForHost(host: String, req: PageRequest): PageResponse<Deeplink> {
        val pageRequest = SpringPageRequest.of(req.offset / req.limit, req.limit, req.sort!!.toSpringSort());
        if (req.filter.isNullOrBlank()) {
            val results = repository.findAllByHostOrderByLastUpdateDateTimeDesc(host, pageRequest)
            return results.toPageResponse(req, DeeplinkDb::toDomain)
        } else {
            val results = repository.findAllByHostAndSearchStringLikeIgnoreCase(host, "%" + req.filter + "%", pageRequest)
            return results.toPageResponse(req, DeeplinkDb::toDomain)
        }
    }

    override fun removeAllExpired(): List<Deeplink> {
        return repository.deleteAllByOptionExpiryDateIsBefore(LocalDate.now().minusDays(1))
            .map { it.toDomain() }
    }

    override fun deleteBySlug(host: String, slug: String) {
        try {
            repository.deleteAllByHostAndSlug(host, slug)
        } catch (e: IOException) {
            logger.error("Error occurred during deeplink deletion: {}", e.message)
            throw IllegalStateException(e)
        }
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
