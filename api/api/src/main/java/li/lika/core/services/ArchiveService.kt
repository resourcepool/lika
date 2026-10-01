package li.lika.core.services

import li.lika.common.exceptions.AccountNotFoundException
import li.lika.common.models.*
import li.lika.common.persistence.AccountDao
import li.lika.common.persistence.DeeplinkDao
import li.lika.common.persistence.DomainDao
import li.lika.common.utils.StringUtils
import li.lika.security.props.SecurityProperties
import org.slf4j.LoggerFactory.getLogger
import org.springframework.cache.annotation.CacheEvict
import org.springframework.security.crypto.password.PasswordEncoder
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
    private val deeplinkDao: DeeplinkDao,
    private val passwordEncoder: PasswordEncoder,
    private val securityProperties: SecurityProperties
) {
    private val logger = getLogger(ArchiveService::class.java)

    @Transactional
    fun exportArchive(): DatabaseArchive {
        val accounts = accountDao.findAll()
        val domains = domainDao.findAll()
        val accountNames = accounts.associate { it.id to it.name }
        val domainHosts = domains.associate { it.id to it.host }
        return DatabaseArchive(
            version = DatabaseArchive.CURRENT_VERSION,
            accounts = accounts.map { acc ->
                val isRoot = acc.defaultAccessToken == securityProperties.defaultGlobalAccessToken
                AccountArchiveItem(
                    name = acc.name,
                    root = isRoot,
                    global = acc.global,
                    // The root account's tokens come from the configuration
                    secretToken = if (isRoot) null else acc.defaultAccessToken,
                    publicReadOnlyToken = if (isRoot) null else acc.defaultPublicReadAccessToken,
                    grants = acc.domainIds.mapNotNull { domainHosts[it] }
                )
            },
            domains = domains.map { d ->
                DomainArchiveItem(
                    standard = d.standard,
                    host = d.host,
                    apiBaseUrl = d.apiBaseUrl,
                    landingBaseUrl = d.landingBaseUrl,
                    owner = d.ownerId?.let { accountNames[it] },
                    links = deeplinkDao.findAllForHost(d.host).map { p ->
                        DeeplinkArchiveItem(
                            targetUrl = p.targetUrl!!,
                            slug = p.slug,
                            type = p.type,
                            options = p.options.toDto(),
                            metadata = p.metadata,
                            passwordHash = p.options.password?.passwordHash,
                            createdBy = accountNames[p.createdBy],
                            createdDateTime = p.createdDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            lastUpdateDateTime = p.lastUpdateDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        )
                    }
                )
            }
        )
    }

    /**
     * Accounts are matched by name, domains by host, links by host and slug.
     * With [dryRun], nothing is written and the report tells what would happen.
     */
    @Transactional
    @CacheEvict(cacheNames = ["accounts", "domains", "deeplinks"], allEntries = true)
    fun importArchive(input: DatabaseArchive, mode: ImportConflictMode, dryRun: Boolean): ImportReport {
        val archive = if (input.version < 2) upgradeV1(input) else input
        val report = ImportReport(dryRun, mode)
        val overwrite = mode == ImportConflictMode.OVERWRITE
        val root = accountDao.findByAccessToken(securityProperties.defaultGlobalAccessToken!!) ?: throw AccountNotFoundException()

        // 1. Accounts, without grants since domains may not exist yet
        val accountIds = HashMap<String, String>()
        val accountsToGrant = ArrayList<Pair<Account, AccountArchiveItem>>()
        archive.accounts.forEach { item ->
            logger.info("Importing account {}", item.name)
            if (item.root) {
                accountIds[item.name] = root.id!!
                report.accounts.skipped++
                return@forEach
            }
            val existing = accountDao.findByName(item.name)
            val account = when {
                existing == null -> {
                    report.accounts.created++
                    Account(
                        UUID.randomUUID().toString(), item.name, item.global,
                        item.secretToken ?: StringUtils.getRandomAlphaNumString(128),
                        item.publicReadOnlyToken ?: StringUtils.getRandomAlphaNumString(128)
                    )
                }
                overwrite -> {
                    report.accounts.updated++
                    existing.copy(
                        global = item.global,
                        defaultAccessToken = item.secretToken ?: existing.defaultAccessToken,
                        defaultPublicReadAccessToken = item.publicReadOnlyToken ?: existing.defaultPublicReadAccessToken
                    )
                }
                else -> {
                    report.accounts.skipped++
                    null
                }
            }
            // Saved now: the database may assign the account a new id, which domains and links refer to
            val saved = if (account != null && !dryRun) accountDao.save(account) else account
            accountIds[item.name] = saved?.id ?: existing!!.id!!
            if (saved != null) {
                accountsToGrant.add(saved to item)
            }
        }

        // 2. Domains and their links
        val domainIds = HashMap<String, String>()
        archive.domains.forEach { item ->
            logger.info("Importing domain {}", item.host)
            val host = item.host.trim().lowercase()
            val existing = domainDao.findByHost(host)
            val ownerId = item.owner?.let { accountIds[it] ?: accountDao.findByName(it)?.id }
            val domain = when {
                existing == null -> {
                    report.domains.created++
                    Domain(UUID.randomUUID().toString(), item.standard, ownerId, host, item.apiBaseUrl, item.landingBaseUrl)
                }
                overwrite -> {
                    report.domains.updated++
                    existing.copy(standard = item.standard, ownerId = ownerId, apiBaseUrl = item.apiBaseUrl, landingBaseUrl = item.landingBaseUrl)
                }
                else -> {
                    report.domains.skipped++
                    existing
                }
            }
            if (!dryRun && domain !== existing) {
                domainDao.save(domain)
            }
            domainIds[host] = domain.id!!
            item.links.forEach { link -> importLink(link, domain, accountIds, root.id!!, overwrite, dryRun, report) }
        }

        // 3. Grants, now that every domain exists
        accountsToGrant.forEach { (account, item) ->
            val grants = item.grants.mapNotNull { domainIds[it] ?: domainDao.findByHost(it)?.id }.toSet()
            if (!dryRun) {
                accountDao.save(account.copy(domainIds = grants))
            }
        }
        return report
    }

    private fun importLink(
        item: DeeplinkArchiveItem,
        domain: Domain,
        accountIds: Map<String, String>,
        rootId: String,
        overwrite: Boolean,
        dryRun: Boolean,
        report: ImportReport
    ) {
        val slug = requireNotNull(item.slug?.takeIf { it.isNotBlank() }) { "Link to ${item.targetUrl} on ${domain.host} has no slug" }
        val existing = deeplinkDao.findByHostAndSlug(domain.host, slug)
        if (existing != null && !overwrite) {
            report.links.skipped++
            return
        }
        if (existing == null) report.links.created++ else report.links.updated++
        if (dryRun) {
            return
        }
        val now = ZonedDateTime.now()
        val options = item.options.toOptions()
        val passwordHash = item.passwordHash ?: item.options.password?.password?.takeIf { it.isNotBlank() }?.let { passwordEncoder.encode(it) }
        options.password = passwordHash?.let { PasswordProtectedOptions(it) }
        deeplinkDao.save(
            Deeplink(
                id = existing?.id ?: UUID.randomUUID().toString(),
                host = domain.host,
                createdBy = item.createdBy?.let { accountIds[it] ?: accountDao.findByName(it)?.id } ?: rootId,
                slug = slug,
                shortenedUrl = "${domain.apiBaseUrl}/$slug",
                targetUrl = item.targetUrl,
                type = item.type,
                createdDateTime = item.createdDateTime?.let { ZonedDateTime.parse(it) } ?: now,
                lastUpdateDateTime = item.lastUpdateDateTime?.let { ZonedDateTime.parse(it) } ?: now,
                options = options,
                metadata = item.metadata
            )
        )
    }

    /**
     * v1 archives nest domains under accounts, and list standard domains under every account.
     * Non-global accounts are granted the standard domains, as they could use them before 0.5.0.
     */
    private fun upgradeV1(archive: DatabaseArchive): DatabaseArchive {
        val domains = LinkedHashMap<String, DomainArchiveItem>()
        archive.accounts.forEach { acc ->
            acc.domains.forEach { d ->
                domains.putIfAbsent(d.host, d.copy(owner = if (d.standard) null else acc.name))
            }
        }
        val standardHosts = domains.values.filter { it.standard }.map { it.host }
        return DatabaseArchive(
            version = DatabaseArchive.CURRENT_VERSION,
            accounts = archive.accounts.map { it.copy(domains = emptyList(), grants = if (it.global) emptyList() else standardHosts) },
            domains = domains.values.toList()
        )
    }
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
