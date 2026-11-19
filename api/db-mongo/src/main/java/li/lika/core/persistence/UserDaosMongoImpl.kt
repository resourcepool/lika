package li.lika.core.persistence

import li.lika.common.models.ApiKey
import li.lika.common.models.User
import li.lika.common.models.UserToken
import li.lika.common.models.UserTokenType
import li.lika.common.persistence.ApiKeyDao
import li.lika.common.persistence.UserDao
import li.lika.common.persistence.UserTokenDao
import li.lika.core.persistence.models.ApiKeyDb
import li.lika.core.persistence.models.UserDb
import li.lika.core.persistence.models.UserTokenDb
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class UserDaoMongoImpl(private val template: MongoTemplate) : UserDao {
    override fun findAll(): List<User> = template.findAll(UserDb::class.java).map { it.toDomain() }
    override fun findById(id: String): User? = template.findById(id, UserDb::class.java)?.toDomain()
    override fun findByEmail(email: String): User? = template.findOne(query(where("email").`is`(email)), UserDb::class.java)?.toDomain()
    override fun findByExternalId(externalId: String): User? = template.findOne(query(where("externalId").`is`(externalId)), UserDb::class.java)?.toDomain()
    override fun save(u: User): User = template.save(UserDb.from(u)).toDomain()
    override fun deleteOne(id: String) {
        template.remove(query(where("_id").`is`(id)), UserDb::class.java)
    }
    override fun deleteAll() {
        template.remove(UserDb::class.java).all()
    }
}

@Repository
class UserTokenDaoMongoImpl(private val template: MongoTemplate) : UserTokenDao {
    override fun findByHash(type: UserTokenType, tokenHash: String): UserToken? =
        template.findOne(query(where("type").`is`(type.name).and("tokenHash").`is`(tokenHash)), UserTokenDb::class.java)?.toDomain()

    override fun findAllForUser(userId: String, type: UserTokenType): List<UserToken> =
        template.find(query(where("userId").`is`(userId).and("type").`is`(type.name)), UserTokenDb::class.java).map { it.toDomain() }

    override fun save(t: UserToken): UserToken = template.save(UserTokenDb.from(t)).toDomain()
    override fun deleteOne(id: String) {
        template.remove(query(where("_id").`is`(id)), UserTokenDb::class.java)
    }
    override fun deleteAllForUser(userId: String, type: UserTokenType?) {
        val criteria = where("userId").`is`(userId)
        if (type != null) criteria.and("type").`is`(type.name)
        template.remove(query(criteria), UserTokenDb::class.java)
    }
    override fun deleteExpired(now: Instant) {
        template.remove(query(where("expiresAt").lte(now)), UserTokenDb::class.java)
    }
    override fun deleteAll() {
        template.remove(UserTokenDb::class.java).all()
    }
}

@Repository
class ApiKeyDaoMongoImpl(private val template: MongoTemplate) : ApiKeyDao {
    override fun findAll(): List<ApiKey> = template.findAll(ApiKeyDb::class.java).map { it.toDomain() }
    override fun findById(id: String): ApiKey? = template.findById(id, ApiKeyDb::class.java)?.toDomain()
    override fun findBySecretHash(secretHash: String): ApiKey? = template.findOne(query(where("secretHash").`is`(secretHash)), ApiKeyDb::class.java)?.toDomain()
    override fun findAllByUserId(userId: String): List<ApiKey> = template.find(query(where("userId").`is`(userId)), ApiKeyDb::class.java).map { it.toDomain() }
    override fun save(k: ApiKey): ApiKey = template.save(ApiKeyDb.from(k)).toDomain()
    override fun deleteOne(id: String) {
        template.remove(query(where("_id").`is`(id)), ApiKeyDb::class.java)
    }
    override fun deleteAll() {
        template.remove(ApiKeyDb::class.java).all()
    }
}
