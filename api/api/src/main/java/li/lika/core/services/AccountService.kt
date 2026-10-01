package li.lika.core.services

import li.lika.common.exceptions.AccountAlreadyExistsException
import li.lika.common.exceptions.AccountNotFoundException
import li.lika.common.exceptions.InvalidDomainException
import li.lika.common.exceptions.LastGlobalAccountException
import li.lika.common.models.Account
import li.lika.common.models.AccountCreator
import li.lika.common.models.TokenKind
import li.lika.common.persistence.AccountDao
import li.lika.common.persistence.DomainDao
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
    private val domainDao: DomainDao,
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

    /**
     * Before 0.5.0, every account could manage the standard (config) domains.
     * Accounts saved by an older version are granted those domains once, so upgrading changes nothing for them.
     */
    @CacheEvict("accounts", allEntries = true)
    fun migrateLegacyGrants() {
        val standardDomainIds = domainDao.findAll().filter { it.standard && it.ownerId == null }.mapNotNull { it.id }.toSet()
        accountDao.findAllPendingGrantsMigration().forEach { account ->
            logger.info("Migrating domain grants of account {} created before 0.5.0", account.name)
            val domainIds = if (account.global) account.domainIds else account.domainIds + standardDomainIds
            accountDao.save(account.copy(domainIds = domainIds))
        }
    }

    @CacheEvict("accounts", allEntries = true)
    fun create(req: AccountCreator): Account {
        val name = req.name.trim()
        require(name.isNotEmpty()) { "Account name is required" }
        if (accountDao.findByName(name) != null) {
            throw AccountAlreadyExistsException()
        }
        assertDomainsExist(req.domainIds)
        return accountDao.save(Account(
            UUID.randomUUID().toString(),
            name,
            req.global,
            StringUtils.getRandomAlphaNumString(128),
            StringUtils.getRandomAlphaNumString(128),
            req.domainIds
        ))
    }

    @CacheEvict("accounts", allEntries = true)
    fun rename(id: String, name: String): Account {
        val account = accountDao.findById(id) ?: throw AccountNotFoundException()
        val newName = name.trim()
        require(newName.isNotEmpty()) { "Account name is required" }
        val homonym = accountDao.findByName(newName)
        if (homonym != null && homonym.id != id) {
            throw AccountAlreadyExistsException()
        }
        return accountDao.save(account.copy(name = newName))
    }

    /**
     * The previous token stops working immediately: the token lookup cache is evicted.
     */
    @CacheEvict("accounts", allEntries = true)
    fun regenerateToken(id: String, kind: TokenKind): Account {
        val account = accountDao.findById(id) ?: throw AccountNotFoundException()
        val token = StringUtils.getRandomAlphaNumString(128)
        return accountDao.save(when (kind) {
            TokenKind.READ_WRITE -> account.copy(defaultAccessToken = token)
            TokenKind.READ -> account.copy(defaultPublicReadAccessToken = token)
        })
    }

    @CacheEvict("accounts", allEntries = true)
    fun updateDomains(id: String, domainIds: Set<String>): Account {
        val account = accountDao.findById(id) ?: throw AccountNotFoundException()
        assertDomainsExist(domainIds)
        return accountDao.save(account.copy(domainIds = domainIds))
    }

    private fun assertDomainsExist(domainIds: Set<String>) {
        if (domainIds.any { domainDao.findOne(it) == null }) {
            throw InvalidDomainException()
        }
    }

    @Cacheable("accounts")
    override fun findByAccessToken(accessToken: String): Account? {
        return accountDao.findByAccessToken(accessToken)
    }

    @CacheEvict("accounts", allEntries = true)
    fun deleteOne(id: String) {
        val account = accountDao.findById(id) ?: throw AccountNotFoundException()
        if (account.global && accountDao.findAll().count { it.global } <= 1) {
            throw LastGlobalAccountException()
        }
        accountDao.deleteOne(id)
    }

    fun findAll(): List<Account> {
        return accountDao.findAll()
    }

    fun findOne(id: String): Account {
        return accountDao.findById(id) ?: throw AccountNotFoundException()
    }
}
