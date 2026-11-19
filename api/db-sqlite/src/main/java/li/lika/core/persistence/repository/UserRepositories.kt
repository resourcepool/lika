package li.lika.core.persistence.repository

import li.lika.core.persistence.models.ApiKeyDb
import li.lika.core.persistence.models.UserDb
import li.lika.core.persistence.models.UserTokenDb
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
interface UserRepository : CrudRepository<UserDb, String> {
    fun findFirstByEmail(email: String): UserDb?
    fun findFirstByExternalId(externalId: String): UserDb?
}

@Repository
interface UserTokenRepository : CrudRepository<UserTokenDb, String> {
    fun findFirstByTypeAndTokenHash(type: String, tokenHash: String): UserTokenDb?
    fun findAllByUserIdAndType(userId: String, type: String): List<UserTokenDb>

    @Transactional
    @Modifying
    @Query("delete from UserTokenDb t where t.userId = :userId")
    fun deleteAllForUser(@Param("userId") userId: String)

    @Transactional
    @Modifying
    @Query("delete from UserTokenDb t where t.userId = :userId and t.type = :type")
    fun deleteAllForUserAndType(@Param("userId") userId: String, @Param("type") type: String)

    @Transactional
    @Modifying
    @Query("delete from UserTokenDb t where t.expiresAt <= :now")
    fun deleteExpired(@Param("now") now: LocalDateTime)
}

@Repository
interface ApiKeyRepository : CrudRepository<ApiKeyDb, String> {
    fun findFirstBySecretHash(secretHash: String): ApiKeyDb?
    fun findAllByUserId(userId: String): List<ApiKeyDb>
}
