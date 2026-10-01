package li.lika.core.persistence

import li.lika.common.models.Domain
import li.lika.common.persistence.DomainDao
import li.lika.core.persistence.models.DomainDb
import mu.KotlinLogging
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.io.IOException
import java.util.*

@Repository
class DomainDaoMongoImpl(private val template: MongoTemplate) : DomainDao {
    private val logger = KotlinLogging.logger { }

    companion object {
        const val ID_PATH = "_id"
        const val HOST_PATH = "host"
        const val OWNER_ID_PATH = "ownerId"
        const val STANDARD_PATH = "standard"
    }

    override fun findOne(id: String): Domain? {
        return template.findOne(Query.query(whereIdEquals(id)), DomainDb::class.java)?.toDomain()
    }

    override fun deleteOne(id: String): Boolean {
        return template.remove(Query.query(whereIdEquals(id)), DomainDb::class.java).deletedCount == 1L
    }

    override fun findByHost(host: String): Domain? {
        val r = template.findOne(Query.query(whereHostIsEqualTo(host)), DomainDb::class.java)
        return r?.toDomain()
    }

    override fun findStandardByHost(host: String): Domain? {
        val r = template.findOne(Query.query(whereHostIsEqualTo(host).andOperator(whereOwnerIdIsNull().andOperator(whereStandardIsTrue()))), DomainDb::class.java)
        return r?.toDomain()
    }

    override fun save(h: Domain): Domain {
        if (h.id == null) {
            h.id = UUID.randomUUID().toString()
        }
        return template.save(DomainDb.from(h)).toDomain()
    }

    override fun findAll(): List<Domain> {
        return template.findAll(DomainDb::class.java)
            .map(DomainDb::toDomain)
    }

    /**
     * Warning! This is voluntarily synchronous
     */
    override fun deleteAll() {
        try {
            template.remove(DomainDb::class.java).all()
        } catch (e: IOException) {
            logger.error("Error occurred during domain deletion: {}", e.message)
            throw IllegalStateException(e)
        }

    }

    private fun whereHostIsEqualTo(host: String): Criteria {
        return Criteria.where(HOST_PATH).`is`(host)
    }

    private fun whereOwnerIdIsNull(): Criteria {
        return Criteria.where(OWNER_ID_PATH).isNull
    }

    private fun whereStandardIsTrue(): Criteria {
        return Criteria.where(STANDARD_PATH).isEqualTo(true)
    }

    private fun whereIdEquals(id: String): Criteria {
        return Criteria.where(ID_PATH).`is`(id)
    }


}
