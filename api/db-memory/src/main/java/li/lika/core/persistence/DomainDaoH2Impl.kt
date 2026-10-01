package li.lika.core.persistence

import li.lika.common.models.Domain
import li.lika.common.persistence.DomainDao
import li.lika.core.persistence.models.DomainDb
import li.lika.core.persistence.models.toDb
import li.lika.core.persistence.repository.DomainRepository
import mu.KotlinLogging
import org.springframework.stereotype.Repository
import java.io.IOException
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Repository
class DomainDaoH2Impl(private val repository: DomainRepository) : DomainDao {
    private val logger = KotlinLogging.logger { }
    override fun findOne(id: String): Domain? {
        return repository.findById(id).getOrNull()?.toDomain()
    }

    override fun deleteOne(id: String): Boolean {
        repository.deleteById(id)
        return true
    }

    override fun findByHost(host: String): Domain? {
        val r = repository.findFirstByHost(host);
        return r.getOrNull()?.toDomain()
    }

    override fun findStandardByHost(host: String): Domain? {
        val r = repository.findFirstByHostAndOwnerIdIsNullAndStandardIsTrue(host);
        return r.getOrNull()?.toDomain()
    }

    override fun save(h: Domain): Domain {
        if (h.id == null) {
            h.id = UUID.randomUUID().toString()
        }
        return repository.save(h.toDb()).toDomain()
    }

    override fun findAll(): List<Domain> {
        return repository.findAll()
            .map(DomainDb::toDomain)
    }

    /**
     * Warning! This is voluntarily synchronous
     */
    override fun deleteAll() {
        try {
            repository.deleteAll()
        } catch (e: IOException) {
            logger.error("Error occurred during domain deletion: {}", e.message)
            throw IllegalStateException(e)
        }

    }

}
