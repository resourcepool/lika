package li.lika.core.persistence

import li.lika.common.models.Deeplink
import li.lika.common.models.dtos.PageRequest
import li.lika.common.models.dtos.PageResponse
import li.lika.common.persistence.DeeplinkDao
import li.lika.core.persistence.models.DeeplinkDb
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
open class DeeplinkDaoMongoImpl(private val template: MongoTemplate) : DeeplinkDao {
    private val logger = KotlinLogging.logger { }

    companion object {
        const val ID_PATH = "_id"
        const val SLUG_PATH = "slug"
        const val HOST_PATH = "host"
        const val TARGET_URL_PATH = "targetUrl"
        const val EXPIRY_DATE_PATH = "optionExpiryDate"
        const val LAST_UPDATE_PATH = "lastUpdateDateTime"
    }

    override fun findOne(id: String): Deeplink? {
        val r = template.findOne(Query.query(whereIdEquals(id)), DeeplinkDb::class.java)
        return r?.toPermalink()
    }

    override fun findByHostAndSlug(host: String, slug: String): Deeplink? {
        val r = template.findOne(
            Query.query(whereHostIsEqualTo(host).andOperator(whereSlugIsEqualTo(slug))),
            DeeplinkDb::class.java
        )
        return r?.toPermalink()
    }

    override fun save(p: Deeplink): Deeplink {
        return template.save(DeeplinkDb.from(p)).toPermalink()
    }

    override fun findAll(): List<Deeplink> {
        return template.findAll(DeeplinkDb::class.java)
            .map(DeeplinkDb::toPermalink)
    }

    override fun findAllForHost(host: String): List<Deeplink> {
        return template.find(
            Query.query(whereHostIsEqualTo(host)).with(Sort.by(Sort.Direction.DESC, LAST_UPDATE_PATH)),
            DeeplinkDb::class.java
        )
            .map(DeeplinkDb::toPermalink)
    }

    override fun removeAllExpired(): List<Deeplink> {
        return template.findAllAndRemove(Query.query(whereExpiryDateBeforeToday()), DeeplinkDb::class.java)
            .map(DeeplinkDb::toPermalink)
    }

    override fun deleteBySlug(host: String, slug: String) {
        try {
            template.remove(
                Query.query(whereHostIsEqualTo(host).andOperator(whereSlugIsEqualTo(slug))),
                DeeplinkDb::class.java
            )
        } catch (e: IOException) {
            logger.error("Error occurred during deeplink deletion: {}", e.message)
            throw IllegalStateException(e)
        }
    }

    override fun deleteOne(id: String) {
        template.remove(Query.query(whereIdEquals(id)), DeeplinkDb::class.java)
    }

    /**
     * Warning! This is voluntarily synchronous
     */
    override fun deleteAll() {
        try {
            template.remove(DeeplinkDb::class.java).all()
        } catch (e: IOException) {
            logger.error("Error occurred during deeplink deletion: {}", e.message)
            throw IllegalStateException(e)
        }
    }

    override fun countForHost(host: String): Int {
        return template.count(Query.query(whereHostIsEqualTo(host)), DeeplinkDb::class.java).toInt()
    }

    override fun deleteAllForHost(host: String) {
        template.remove(Query.query(whereHostIsEqualTo(host)), DeeplinkDb::class.java)
    }

    override fun findPageForHosts(hosts: Collection<String>, req: PageRequest): PageResponse<Deeplink> {
        val criteria = Criteria.where(HOST_PATH).`in`(hosts)
        if (!req.filter.isNullOrBlank()) {
            val pattern = Regex.escape(req.filter!!)
            criteria.orOperator(
                Criteria.where(SLUG_PATH).regex(pattern, "i"),
                Criteria.where(TARGET_URL_PATH).regex(pattern, "i"),
                Criteria.where(HOST_PATH).regex(pattern, "i")
            )
        }
        val pageQuery = Query.query(criteria).with(
            org.springframework.data.domain.PageRequest.of(
                req.offset / req.limit,
                req.limit,
                req.sort!!.toSpringSort()
            )
        )
        val result = template.find(pageQuery, DeeplinkDb::class.java)
            .map(DeeplinkDb::toPermalink)
        val totalItemCount = template.count(Query.query(criteria), DeeplinkDb::class.java)
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
        return Criteria.where(EXPIRY_DATE_PATH).lt(LocalDate.now().minusDays(1))
    }


}
