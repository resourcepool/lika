package li.lika.core.services

import li.lika.common.exceptions.AccountAlreadyExistsException
import li.lika.common.models.Account
import li.lika.common.models.AccountCreator
import li.lika.common.persistence.AccountDao
import li.lika.common.security.AccountRegistry
import li.lika.common.utils.StringUtils
import li.lika.security.props.SecurityProperties
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.*

@Service
class AccountService(
    private val accountDao: AccountDao,
    private val securityProperties: SecurityProperties
) : AccountRegistry {

    private val logger = org.slf4j.LoggerFactory.getLogger(AccountService::class.java)

    @CacheEvict("accounts", allEntries = true)
    fun init() {

        val org = accountDao.findByAccessToken(securityProperties.defaultGlobalAccessToken!!)

        if (org == null) {
            accountDao.save(
                Account(
                    UUID.randomUUID().toString(),
                    securityProperties.defaultGlobalName!!,
                    true,
                    securityProperties.defaultGlobalAccessToken!!,
                    securityProperties.defaultGlobalPublicReadAccessToken!!
                )
            )
        }
    }

    @CacheEvict("accounts", allEntries = true)
    fun create(req: AccountCreator): Account {
        val a = accountDao.findByName(req.name!!)
        if (a != null) {
            throw AccountAlreadyExistsException()
        }
        return accountDao.save(Account(
            UUID.randomUUID().toString(),
            req.name!!,
            false,
            StringUtils.getRandomAlphaNumString(128),
            StringUtils.getRandomAlphaNumString(128)
        ))
    }

    @Cacheable("accounts")
    override fun findByAccessToken(accessToken: String): Account? {
        return accountDao.findByAccessToken(accessToken)
    }

    @CacheEvict("accounts", allEntries = true)
    fun deleteOne(id: String) {
        accountDao.deleteOne(id)
    }

    fun findAll(): List<Account> {
        return accountDao.findAll()
    }

    @Cacheable("accounts")
    fun findOne(id: String): Account? {
        return accountDao.findById(id)
    }
}
