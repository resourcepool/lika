package li.lika.core.persistence

import li.lika.common.models.Account
import li.lika.common.persistence.AccountDao
import li.lika.core.persistence.models.AccountDb
import li.lika.core.persistence.models.toDb
import mu.KotlinLogging
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.io.IOException

@Repository
class AccountDaoMongoImpl(private val template: MongoTemplate) : AccountDao {
    private val logger = KotlinLogging.logger { }

    companion object {
        const val ID_PATH = "_id"
        const val NAME_PATH = "name"
        const val ACCESS_TOKEN_PATH = "defaultAccessToken"
        const val PUBLIC_READ_ACCESS_TOKEN_PATH = "defaultPublicReadAccessToken"
        const val GRANTS_VERSION_PATH = "grantsVersion"
    }

    override fun findAll(): List<Account> {
        return template.findAll(AccountDb::class.java).map { it.toDomain() }
    }

    override fun findById(id: String): Account? {
        val r = template.findOne(Query.query(Criteria().orOperator(whereIdEquals(id))), AccountDb::class.java)
        return r?.toDomain()
    }

    override fun findByName(name: String): Account? {
        val r = template.findOne(Query.query(Criteria().orOperator(whereNameEquals(name))), AccountDb::class.java)
        return r?.toDomain()
    }

    override fun findByAccessToken(accessToken: String): Account? {
        val r = template.findOne(Query.query(Criteria().orOperator(whereDefaultAccessTokenIsEqualTo(accessToken), whereDefaultPublicReadAccessTokenIsEqualTo(accessToken))), AccountDb::class.java)
        return r?.toDomain()
    }

    override fun findAllPendingGrantsMigration(): List<Account> {
        return template.find(Query.query(Criteria.where(GRANTS_VERSION_PATH).isNull), AccountDb::class.java).map { it.toDomain() }
    }

    override fun save(o: Account): Account {
        return template.save(o.toDb()).toDomain()
    }

    override fun deleteOne(id: String): Boolean {
        return template.remove(Query.query(whereIdEquals(id)), AccountDb::class.java).deletedCount == 1L
    }

    /**
     * Warning! This is voluntarily synchronous
     */
    override fun deleteAll() {
        try {
            template.remove(AccountDb::class.java).all()
        } catch (e: IOException) {
            logger.error("Error occurred during domain deletion: {}", e.message)
            throw IllegalStateException(e)
        }

    }

    private fun whereDefaultAccessTokenIsEqualTo(accessToken: String): Criteria {
        return Criteria.where(ACCESS_TOKEN_PATH).`is`(accessToken)
    }
    private fun whereDefaultPublicReadAccessTokenIsEqualTo(accessToken: String): Criteria {
        return Criteria.where(PUBLIC_READ_ACCESS_TOKEN_PATH).`is`(accessToken)
    }

    private fun whereIdEquals(id: String): Criteria {
        return Criteria.where(ID_PATH).`is`(id)
    }

    private fun whereNameEquals(name: String): Criteria {
        return Criteria.where(NAME_PATH).`is`(name)
    }


}
