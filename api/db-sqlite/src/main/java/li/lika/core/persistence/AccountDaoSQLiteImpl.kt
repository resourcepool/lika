package li.lika.core.persistence

import li.lika.common.models.Account
import li.lika.common.persistence.AccountDao
import li.lika.core.persistence.models.toDb
import li.lika.core.persistence.repository.AccountRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.io.IOException
import kotlin.jvm.optionals.getOrNull

@Service
class AccountDaoSQLiteImpl(private val repository: AccountRepository) : AccountDao {
    private val logger = KotlinLogging.logger { }

    override fun findAll(): List<Account> {
        return repository.findAll().map { it.toDomain() }
    }

    override fun findById(id: String): Account? {
        val r = repository.findById(id).getOrNull()
        return r?.toDomain()
    }

    override fun findByName(name: String): Account? {
        val r = repository.findFirstByName(name).getOrNull()
        return r?.toDomain()
    }

    override fun findByAccessToken(accessToken: String): Account? {
        val r = repository.findFirstByDefaultPublicReadAccessTokenOrDefaultAccessToken(accessToken, accessToken).getOrNull()
        return r?.toDomain()
    }

    override fun save(o: Account): Account {
        return repository.save(o.toDb()).toDomain()
    }

    override fun deleteOne(id: String): Boolean {
        repository.deleteById(id)
        return true
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
