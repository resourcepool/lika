package li.lika.common.persistence

import li.lika.common.models.ApiKey
import li.lika.common.models.User
import li.lika.common.models.UserToken
import li.lika.common.models.UserTokenType
import java.time.Instant

interface UserDao {
    fun findAll(): List<User>
    fun findById(id: String): User?
    fun findByEmail(email: String): User?
    fun findByExternalId(externalId: String): User?
    fun save(u: User): User
    fun deleteOne(id: String)
    fun deleteAll()
}

interface UserTokenDao {
    fun findByHash(type: UserTokenType, tokenHash: String): UserToken?
    fun findAllForUser(userId: String, type: UserTokenType): List<UserToken>
    fun save(t: UserToken): UserToken
    fun deleteOne(id: String)
    fun deleteAllForUser(userId: String, type: UserTokenType? = null)
    fun deleteExpired(now: Instant)
    fun deleteAll()
}

interface ApiKeyDao {
    fun findAll(): List<ApiKey>
    fun findById(id: String): ApiKey?
    fun findBySecretHash(secretHash: String): ApiKey?
    fun findAllByUserId(userId: String): List<ApiKey>
    fun save(k: ApiKey): ApiKey
    fun deleteOne(id: String)
    fun deleteAll()
}
