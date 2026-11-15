package li.lika.core.persistence

import li.lika.common.models.ApiKey
import li.lika.common.models.User
import li.lika.common.models.UserToken
import li.lika.common.models.UserTokenType
import li.lika.common.persistence.ApiKeyDao
import li.lika.common.persistence.UserDao
import li.lika.common.persistence.UserTokenDao
import li.lika.core.persistence.models.toDb
import li.lika.core.persistence.models.toUtc
import li.lika.core.persistence.repository.ApiKeyRepository
import li.lika.core.persistence.repository.UserRepository
import li.lika.core.persistence.repository.UserTokenRepository
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@Service
class UserDaoJpaImpl(private val repository: UserRepository) : UserDao {
    override fun findAll(): List<User> = repository.findAll().map { it.toDomain() }
    override fun findById(id: String): User? = repository.findById(id).getOrNull()?.toDomain()
    override fun findByEmail(email: String): User? = repository.findFirstByEmail(email)?.toDomain()
    override fun findByExternalId(externalId: String): User? = repository.findFirstByExternalId(externalId)?.toDomain()
    override fun save(u: User): User = repository.save(u.toDb()).toDomain()
    override fun deleteOne(id: String) = repository.deleteById(id)
    override fun deleteAll() = repository.deleteAll()
}

@Service
class UserTokenDaoJpaImpl(private val repository: UserTokenRepository) : UserTokenDao {
    override fun findByHash(type: UserTokenType, tokenHash: String): UserToken? =
        repository.findFirstByTypeAndTokenHash(type.name, tokenHash)?.toDomain()

    override fun findAllForUser(userId: String, type: UserTokenType): List<UserToken> =
        repository.findAllByUserIdAndType(userId, type.name).map { it.toDomain() }

    override fun save(t: UserToken): UserToken = repository.save(t.toDb()).toDomain()
    override fun deleteOne(id: String) = repository.deleteById(id)

    override fun deleteAllForUser(userId: String, type: UserTokenType?) {
        if (type == null) repository.deleteAllForUser(userId) else repository.deleteAllForUserAndType(userId, type.name)
    }

    override fun deleteExpired(now: Instant) = repository.deleteExpired(now.toUtc())
    override fun deleteAll() = repository.deleteAll()
}

@Service
class ApiKeyDaoJpaImpl(private val repository: ApiKeyRepository) : ApiKeyDao {
    override fun findAll(): List<ApiKey> = repository.findAll().map { it.toDomain() }
    override fun findById(id: String): ApiKey? = repository.findById(id).getOrNull()?.toDomain()
    override fun findBySecretHash(secretHash: String): ApiKey? = repository.findFirstBySecretHash(secretHash)?.toDomain()
    override fun findByUserId(userId: String): ApiKey? = repository.findFirstByUserId(userId)?.toDomain()
    override fun save(k: ApiKey): ApiKey = repository.save(k.toDb()).toDomain()
    override fun deleteOne(id: String) = repository.deleteById(id)
    override fun deleteAll() = repository.deleteAll()
}
