package li.lika.core.services

import li.lika.common.models.*
import li.lika.common.persistence.ApiKeyDao
import li.lika.common.persistence.DeeplinkDao
import li.lika.common.persistence.DomainDao
import li.lika.common.persistence.UserDao
import li.lika.core.services.auth.Secrets
import org.slf4j.LoggerFactory.getLogger
import org.springframework.cache.annotation.CacheEvict
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class ArchiveService(
    private val userDao: UserDao,
    private val keyDao: ApiKeyDao,
    private val domainDao: DomainDao,
    private val deeplinkDao: DeeplinkDao,
    private val passwordEncoder: PasswordEncoder
) {
    private val logger = getLogger(ArchiveService::class.java)

    /**
     * Users and keys refer to domains by host, links to their creator by email or key name.
     * Secrets are only exported hashed.
     */
    @Transactional
    fun exportArchive(): DatabaseArchive {
        val users = userDao.findAll()
        val keys = keyDao.findAll()
        val domains = domainDao.findAll()
        val hosts = domains.associate { it.id to it.host }
        val creators = users.associate { it.id to it.email } + keys.associate { it.id to it.name }
        return DatabaseArchive(
            version = DatabaseArchive.CURRENT_VERSION,
            users = users.map { u ->
                UserArchiveItem(u.email, u.name, u.status, u.admin, u.domains.mapKeys { hosts[it.key] ?: it.key }, u.passwordHash, u.createdAt.toString())
            },
            apiKeys = keys.map { k ->
                ApiKeyArchiveItem(k.name, k.admin, k.domains.mapKeys { hosts[it.key] ?: it.key }, k.secretHash, k.prefix, k.createdAt.toString(), k.expiresAt?.toString(),
                    k.userId?.let { id -> users.firstOrNull { it.id == id }?.email })
            },
            domains = domains.map { d ->
                DomainArchiveItem(
                    standard = d.standard,
                    host = d.host,
                    apiBaseUrl = d.apiBaseUrl,
                    landingBaseUrl = d.landingBaseUrl,
                    links = deeplinkDao.findAllForHost(d.host).map { p ->
                        DeeplinkArchiveItem(
                            targetUrl = p.targetUrl!!,
                            slug = p.slug,
                            type = p.type,
                            options = p.options.toDto(),
                            metadata = p.metadata,
                            passwordHash = p.options.password?.passwordHash,
                            createdBy = creators[p.createdBy] ?: p.createdBy,
                            createdDateTime = p.createdDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            lastUpdateDateTime = p.lastUpdateDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        )
                    }
                )
            }
        )
    }

    /**
     * Users are matched by email, keys by name, domains by host, links by host and slug.
     * Accounts of v1 and v2 archives become API keys, as when upgrading Lika.
     * With [dryRun], nothing is written and the report tells what would happen.
     */
    @Transactional
    @CacheEvict(cacheNames = ["users", "apikeys", "domains", "deeplinks"], allEntries = true)
    fun importArchive(input: DatabaseArchive, mode: ImportConflictMode, dryRun: Boolean): ImportReport {
        val v2 = if (input.version < 2) upgradeV1(input) else input
        val archive = if (v2.version < 3) upgradeV2(v2) else v2
        val report = ImportReport(dryRun, mode)
        val overwrite = mode == ImportConflictMode.OVERWRITE

        // 1. Domains, which users and keys refer to
        val domains = HashMap<String, Domain>()
        archive.domains.forEach { item ->
            logger.info("Importing domain {}", item.host)
            val host = item.host.trim().lowercase()
            val existing = domainDao.findByHost(host)
            val domain = when {
                existing == null -> {
                    report.domains.created++
                    Domain(UUID.randomUUID().toString(), item.standard, null, host, item.apiBaseUrl, item.landingBaseUrl)
                }
                overwrite -> {
                    report.domains.updated++
                    existing.copy(standard = item.standard, apiBaseUrl = item.apiBaseUrl, landingBaseUrl = item.landingBaseUrl)
                }
                else -> {
                    report.domains.skipped++
                    existing
                }
            }
            if (!dryRun && domain !== existing) {
                domainDao.save(domain)
            }
            domains[host] = domain
        }
        fun roles(byHost: Map<String, DomainRole>) = byHost.mapNotNull { (host, role) ->
            val h = host.trim().lowercase()
            (domains[h]?.id ?: domainDao.findByHost(h)?.id)?.let { it to role }
        }.toMap()

        // 2. Users and keys
        val creators = HashMap<String, String>()
        archive.users.forEach { item ->
            val email = item.email.trim().lowercase()
            val existing = userDao.findByEmail(email)
            val user = when {
                existing == null -> {
                    report.users.created++
                    User(UUID.randomUUID().toString(), email, item.name, item.status, item.admin, roles(item.domains),
                        passwordHash = item.passwordHash, createdAt = item.createdAt?.let { Instant.parse(it) } ?: Instant.now())
                }
                overwrite -> {
                    report.users.updated++
                    existing.copy(name = item.name, status = item.status, admin = item.admin, domains = roles(item.domains),
                        passwordHash = item.passwordHash ?: existing.passwordHash, version = existing.version + 1)
                }
                else -> {
                    report.users.skipped++
                    null
                }
            }
            if (user != null && !dryRun) userDao.save(user)
            creators[email] = (user ?: existing)!!.id
        }
        archive.apiKeys.filter { it.user == null || creators.containsKey(it.user!!.trim().lowercase()) }.forEach { item ->
            // Application keys are matched by name; user keys by owner and name, as two users may name theirs alike
            val owner = item.user?.let { creators[it.trim().lowercase()] }
            val existing = keyDao.findAll().firstOrNull { it.name == item.name && it.userId == owner }
            val key = when {
                existing == null -> {
                    report.apiKeys.created++
                    ApiKey(UUID.randomUUID().toString(), item.name, item.admin, roles(item.domains), item.secretHash, item.prefix,
                        createdAt = item.createdAt?.let { Instant.parse(it) } ?: Instant.now(), expiresAt = item.expiresAt?.let { Instant.parse(it) },
                        userId = owner)
                }
                overwrite -> {
                    report.apiKeys.updated++
                    existing.copy(admin = item.admin, domains = roles(item.domains), secretHash = item.secretHash, prefix = item.prefix,
                        expiresAt = item.expiresAt?.let { Instant.parse(it) })
                }
                else -> {
                    report.apiKeys.skipped++
                    null
                }
            }
            // A secret already used by another key (e.g. the configured root key) is not imported twice
            val clash = key?.let { keyDao.findBySecretHash(it.secretHash) }?.takeIf { it.id != key.id }
            if (key != null && clash == null && !dryRun) keyDao.save(key)
            // Links are never created by a user key: its user is their creator
            if (owner == null) creators[item.name] = (clash ?: key ?: existing)!!.id
        }

        // 3. Links
        archive.domains.forEach { item ->
            val domain = domains[item.host.trim().lowercase()]!!
            item.links.forEach { link -> importLink(link, domain, creators, overwrite, dryRun, report) }
        }
        return report
    }

    private fun importLink(
        item: DeeplinkArchiveItem,
        domain: Domain,
        creators: Map<String, String>,
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
                createdBy = item.createdBy?.let { creators[it.lowercase()] ?: creators[it] } ?: DeeplinkService.SYSTEM_CREATOR,
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
            version = 2,
            accounts = archive.accounts.map { it.copy(domains = emptyList(), grants = if (it.global) emptyList() else standardHosts) },
            domains = domains.values.toList()
        )
    }

    /**
     * v2 accounts become API keys, like the accounts of a database upgraded to 0.7.0.
     * The root account is skipped: its tokens come from the configuration.
     */
    private fun upgradeV2(archive: DatabaseArchive): DatabaseArchive {
        val allHosts = archive.domains.map { it.host }.toSet()
        val keys = archive.accounts.filter { !it.root }.flatMap { acc ->
            val hosts = (acc.grants + archive.domains.filter { it.owner == acc.name }.map { it.host }).toSet()
            val readHosts = if (acc.global) allHosts else hosts
            listOfNotNull(
                acc.secretToken?.takeIf { acc.global || hosts.isNotEmpty() }?.let {
                    ApiKeyArchiveItem(acc.name, acc.global, if (acc.global) emptyMap() else hosts.associateWith { DomainRole.EDITOR }, Secrets.sha256(it), Secrets.displayPrefix(it))
                },
                acc.publicReadOnlyToken?.takeIf { readHosts.isNotEmpty() }?.let {
                    ApiKeyArchiveItem("${acc.name} (read)", false, readHosts.associateWith { DomainRole.VIEWER }, Secrets.sha256(it), Secrets.displayPrefix(it))
                }
            )
        }
        return archive.copy(version = DatabaseArchive.CURRENT_VERSION, accounts = emptyList(), apiKeys = keys, domains = archive.domains.map { it.copy(owner = null) })
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
