package li.lika.core.services.auth

import li.lika.common.models.Account
import li.lika.common.models.ApiKey
import li.lika.common.models.DomainRole
import li.lika.common.persistence.AccountDao
import li.lika.common.persistence.ApiKeyDao
import li.lika.common.persistence.DomainDao
import li.lika.security.props.SecurityProperties
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Lika < 0.7.0 had accounts, each holding a read-write and a read token. They become API keys,
 * keeping their token values so that applications keep working:
 * - read-write token of a global account → admin key
 * - read-write token of another account → editor key on its granted and owned domains
 * - read token → viewer key on the same domains (every domain for a global account)
 */
@Service
class AccessMigrationService(
    private val accountDao: AccountDao,
    private val domainDao: DomainDao,
    private val keyDao: ApiKeyDao,
    private val securityProperties: SecurityProperties
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    fun migrate() {
        val accounts = accountDao.findAll()
        if (accounts.isNotEmpty()) {
            logger.info("Migrating {} account(s) of Lika < 0.7.0 to API keys", accounts.size)
            val domains = domainDao.findAll()
            accounts.forEach { account ->
                val owned = domains.filter { it.ownerId == account.id }.mapNotNull { it.id }
                migrate(account, account.domainIds + owned, domains.mapNotNull { it.id })
            }
            accountDao.deleteAll()
            domains.filter { it.ownerId != null }.forEach { domainDao.save(it.copy(ownerId = null)) }
        }
        migrateConfiguredRoot()
    }

    private fun migrate(account: Account, domainIds: Set<String>, allDomainIds: List<String>) {
        if (isPublicDevToken(account.defaultAccessToken) || isPublicDevToken(account.defaultPublicReadAccessToken)) {
            // Lika < 0.7.0 created this account from its development defaults, even in production
            logger.warn("Not migrating account {}: its tokens are the public development ones. Create API keys in the web app instead.", account.name)
            return
        }
        val readDomains = if (account.global) allDomainIds else domainIds
        if (account.global) {
            createKey(account.defaultAccessToken, account.name, admin = true)
        } else if (domainIds.isNotEmpty()) {
            createKey(account.defaultAccessToken, account.name, domains = domainIds.associateWith { DomainRole.EDITOR })
        }
        if (readDomains.isNotEmpty()) {
            createKey(account.defaultPublicReadAccessToken, "${account.name} (read)", domains = readDomains.associateWith { DomainRole.VIEWER })
        }
    }

    /**
     * The deprecated root account of the configuration keeps working as an admin key.
     */
    private fun migrateConfiguredRoot() {
        val name = securityProperties.defaultGlobalName ?: "root"
        securityProperties.defaultGlobalAccessToken?.takeIf { it.isNotBlank() }?.let {
            logger.warn("security.default-global-access-token is deprecated: it is now the admin API key \"{}\". Create keys in the web app instead.", name)
            createKey(it, name, admin = true)
        }
        securityProperties.defaultGlobalPublicReadAccessToken?.takeIf { it.isNotBlank() }?.let {
            val domains = domainDao.findAll().mapNotNull { d -> d.id }
            if (domains.isNotEmpty()) createKey(it, "$name (read)", domains = domains.associateWith { DomainRole.VIEWER })
        }
    }

    /** Tokens of application-dev.yml, which earlier versions applied whenever no profile was active */
    private fun isPublicDevToken(token: String): Boolean =
        token in PUBLIC_DEV_TOKENS && token != securityProperties.defaultGlobalAccessToken && token != securityProperties.defaultGlobalPublicReadAccessToken

    private fun createKey(secret: String, name: String, admin: Boolean = false, domains: Map<String, DomainRole> = emptyMap()) {
        val hash = Secrets.sha256(secret)
        if (keyDao.findBySecretHash(hash) != null) return
        keyDao.save(ApiKey(UUID.randomUUID().toString(), name, admin, domains, hash, Secrets.displayPrefix(secret)))
    }

    companion object {
        val PUBLIC_DEV_TOKENS = setOf("lika-client-secret", "lika-client-read")
    }
}
