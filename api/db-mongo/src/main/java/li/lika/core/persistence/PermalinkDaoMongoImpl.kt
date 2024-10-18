package li.lika.core.persistence

import li.lika.common.models.Permalink
import li.lika.common.models.dtos.PageRequest
import li.lika.common.models.dtos.PageResponse
import li.lika.common.persistence.PermalinkDao
import li.lika.core.persistence.models.PermalinkDb
import li.lika.core.persistence.utils.toSpringSort
import mu.KotlinLogging
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.io.IOException
import java.time.LocalDate

@Repository
open class PermalinkDaoMongoImpl(private val template: MongoTemplate) : PermalinkDao {
    private val logger = KotlinLogging.logger { }

    companion object {
        const val ID_PATH = "_id"
        const val SLUG_PATH = "slug"
        const val HOST_PATH = "host"
        const val EXPIRY_DATE_PATH = "expiryDate"
        const val LAST_UPDATE_PATH = "lastUpdateDateTime"
    }

    override fun findOne(id: String): Permalink? {
        val r = template.findOne(Query.query(whereIdEquals(id)), PermalinkDb::class.java)
        return r?.toPermalink()
    }

    override fun findByHostAndSlug(host: String, slug: String): Permalink? {
        val r = template.findOne(
            Query.query(whereHostIsEqualTo(host).andOperator(whereSlugIsEqualTo(slug))),
            PermalinkDb::class.java
        )
        return r?.toPermalink()
    }

    override fun save(p: Permalink): Permalink {
        return template.save(PermalinkDb.from(p)).toPermalink()
    }

    override fun findAll(): List<Permalink> {
        return template.findAll(PermalinkDb::class.java)
            .map(PermalinkDb::toPermalink)
    }

    override fun findAllForHost(host: String): List<Permalink> {
        return template.find(
            Query.query(whereHostIsEqualTo(host)).with(Sort.by(Sort.Direction.DESC, LAST_UPDATE_PATH)),
            PermalinkDb::class.java
        )
            .map(PermalinkDb::toPermalink)
    }

    override fun removeAllExpired(): List<Permalink> {
        return template.findAllAndRemove(Query.query(whereExpiryDateBeforeToday()), PermalinkDb::class.java)
            .map(PermalinkDb::toPermalink)
    }

    override fun deleteBySlug(host: String, slug: String) {
        try {
            template.remove(
                Query.query(whereHostIsEqualTo(host).andOperator(whereSlugIsEqualTo(slug))),
                PermalinkDb::class.java
            )
        } catch (e: IOException) {
            logger.error("Error occurred during permalink deletion: {}", e.message)
            throw IllegalStateException(e)
        }
    }

    override fun deleteOne(id: String) {
        template.remove(Query.query(whereIdEquals(id)), PermalinkDb::class.java)
    }

    /**
     * Warning! This is voluntarily synchronous
     */
    override fun deleteAll() {
        try {
            template.remove(PermalinkDb::class.java).all()
        } catch (e: IOException) {
            logger.error("Error occurred during permalink deletion: {}", e.message)
            throw IllegalStateException(e)
        }
    }

    override fun findPageForHost(host: String, req: PageRequest): PageResponse<Permalink> {
        // FIXME to be tested
        // FIXME filter not implemented
        val query = Query.query(whereHostIsEqualTo(host))
        val pageQuery = Query.query(whereHostIsEqualTo(host)).with(
            org.springframework.data.domain.PageRequest.of(
                req.offset / req.limit,
                req.limit,
                req.sort!!.toSpringSort()
            )
        )
        val result = template.find(pageQuery, PermalinkDb::class.java)
            .map(PermalinkDb::toPermalink)
        val totalItemCount = template.count(query, PermalinkDb::class.java)
        return PageResponse(req, totalItemCount.toInt(), result)
    }

    private fun whereSlugIsEqualTo(slug: String): Criteria {
        return Criteria.where(SLUG_PATH).`is`(slug)
    }

    private fun whereHostIsEqualTo(host: String): Criteria {
        return Criteria.where(HOST_PATH).`is`(host)
    }

    private fun whereIdEquals(id: String): Criteria {
        return Criteria.where(ID_PATH).`is`(id)
    }

    private fun whereExpiryDateBeforeToday(): Criteria {
        return Criteria.where(EXPIRY_DATE_PATH).lte(LocalDate.now().minusDays(1))
    }


}
