package li.lika.core.services

import li.lika.common.exceptions.AccountNotFoundException
import li.lika.common.models.*
import li.lika.common.persistence.AccountDao
import li.lika.common.persistence.DomainDao
import li.lika.common.persistence.PermalinkDao
import li.lika.core.props.LikaProperties
import li.lika.core.services.handlers.TransactionHandler
import li.lika.security.props.SecurityProperties
import org.slf4j.LoggerFactory.getLogger
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class ArchiveService(
    private val accountDao: AccountDao,
    private val domainDao: DomainDao,
    private val permalinkDao: PermalinkDao,
    private val permalinkService: PermalinkService,
    private val transactionHandler: TransactionHandler,
    private val securityProperties: SecurityProperties,
    private val props: LikaProperties
) {
    private val logger = getLogger(ArchiveService::class.java)

    @Transactional
    @CacheEvict(cacheNames = ["accounts", "domains", "permalinks"], allEntries = true)
    fun exportArchive(): DatabaseArchive {
        val targetAccounts = ArrayList<AccountArchiveItem>()
        val archive = DatabaseArchive(targetAccounts)
        accountDao.findAll().forEach { acc ->
            val targetDomains = ArrayList<DomainArchiveItem>()
            val domains = domainDao.findAllByOwnerId(acc.id!!)
            val isRoot = acc.global && acc.name == securityProperties.defaultGlobalName
            targetAccounts.add(AccountArchiveItem.fromAccount(acc, targetDomains, isRoot))
            domains.forEach { d ->
                val targetPermalinks = ArrayList<PermalinkArchiveItem>()
                val permalinks = permalinkDao.findAllForHost(d.host)
                targetDomains.add(DomainArchiveItem.fromDomain(d, targetPermalinks))
                permalinks.forEach { p ->
                    targetPermalinks.add(PermalinkArchiveItem(p.targetUrl, p.slug, p.type, p.options.toDto()))
                }
            }
        }
        return archive
    }

    @Transactional
    fun importArchive(archive: DatabaseArchive) {
        // Import accounts
        archive.accounts.forEach { acc ->
            var account: Account = importAccount(acc)
            // Import domains for each account
            acc.domains.forEach { d ->
                val domain = importDomain(account.id!!, d)
                // Import permalinks for each domain
                d.links.forEach { p ->
                    val permalink = importPermalink(domain.ownerId!!, domain.id!!, domain.host, p)
                    permalinkDao.save(permalink)
                }
            }
        }
    }

    private fun importPermalink(ownerId: String, domainId: String, host: String, item: PermalinkArchiveItem): Permalink {
        return transactionHandler.runInTransaction {
            logger.info("Importing permalink {}", item.slug)
            var permalink: Permalink? = permalinkDao.findByHostAndSlug(host, item.slug!!)
            if (permalink != null) {
                logger.info("Found permalink {}: {}", permalink.slug, permalink.id)
                return@runInTransaction permalink
            }
            logger.info("No matching permalink, will create one")
            var permalinkCreator = PermalinkCreator(
                null,
                item.targetUrl,
                domainId,
                item.slug,
                item.type,
                item.options
            )
            return@runInTransaction permalinkService.save(permalinkCreator, ownerId)
        }
    }

    private fun importAccount(acc: AccountArchiveItem): Account {
        return transactionHandler.runInTransaction {
            logger.info("Importing account {}", acc.name)
            var account: Account? = accountDao.findByName(acc.name)
            if (acc.root) {
                return@runInTransaction accountDao.findByAccessToken(securityProperties.defaultGlobalAccessToken!!)
                    ?: throw AccountNotFoundException()
            }
            if (account != null) {
                logger.info("Found account {}: {}", account.name, account.id)
            } else {
                logger.info("No matching account, will create one")
                account = Account(UUID.randomUUID().toString(), acc.name, acc.global, acc.secretToken!!, acc.publicReadOnlyToken!!)
            }
            return@runInTransaction accountDao.save(account)
        }

    }

    private fun importDomain(ownerId: String, dom: DomainArchiveItem): Domain {
        return transactionHandler.runInTransaction {
            logger.info("Importing domain {}", dom.host)
            var domain: Domain? = domainDao.findStandardByHost(dom.host)
            if (domain != null) {
                logger.info("Found domain {}: {}", dom.host, domain.id)
                if (domain.ownerId != ownerId) {
                    logger.warn("Domain {} already belongs to another owner: {}", dom.host, domain.ownerId)
                    throw IllegalStateException("Domain ${dom.host} already exists for another owner: ${domain.ownerId}. Will cancel import.")
                }
            } else {
                logger.info("No matching domain, will create one")
                domain = Domain(UUID.randomUUID().toString(), dom.standard, ownerId, dom.host, dom.apiBaseUrl, dom.landingBaseUrl)
            }
            return@runInTransaction domainDao.save(domain)
        }
    }
}


fun DomainArchiveItem.Companion.fromDomain(
    domain: Domain,
    targetPermalinks: ArrayList<PermalinkArchiveItem>
): DomainArchiveItem {
    return DomainArchiveItem(
        domain.standard,
        domain.host,
        domain.apiBaseUrl,
        domain.landingBaseUrl,
        targetPermalinks
    )
}

fun AccountArchiveItem.Companion.fromAccount(
    acc: Account,
    domains: List<DomainArchiveItem>,
    isRoot: Boolean
): AccountArchiveItem {
    return AccountArchiveItem(
        acc.name,
        isRoot,
        acc.global,
        if (isRoot) null else acc.defaultAccessToken,
        if (isRoot) null else acc.defaultPublicReadAccessToken,
        domains
    )
}


private fun Options.toDto(): OptionsDto {
    return OptionsDto(
        landing = this.landing?.let { LandingOptionsDto(it.templateId) },
        expiry = this.expiry?.let { ExpiryOptionsDto(it.expiryDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)) },
        activation = this.activation?.let {
            ActivationOptionsDto(
                it.activationInstant?.format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                it.targetUrlBeforeActivation
            )
        }
    )

}

fun OptionsDto.toOptions(): Options {
    val o = Options()
    if (landing?.templateId != null) {
        o.landing = LandingOptions(landing!!.templateId)
    }
    if (expiry?.expiryDate != null) {
        o.expiry = ExpiryOptions(LocalDate.parse(expiry!!.expiryDate!!))
    }
    if (activation?.activationInstant != null) {
        o.activation = ActivationOptions(
            ZonedDateTime.parse(activation!!.activationInstant!!),
            activation!!.targetUrlBeforeActivation)
    }
    return o
}
