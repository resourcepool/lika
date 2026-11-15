package li.lika.core.services

import li.lika.common.exceptions.*
import li.lika.common.models.*
import li.lika.common.models.ProtectedLinks.isForbidden
import li.lika.common.models.dtos.PageRequest
import li.lika.common.models.dtos.PageResponse
import li.lika.common.persistence.DomainDao
import li.lika.common.persistence.DeeplinkDao
import li.lika.common.utils.StringUtils
import li.lika.core.props.LikaProperties
import li.lika.core.services.handlers.TransactionHandler
import li.lika.common.security.LikaPrincipal
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*

@Service
class DeeplinkService(
    private val domainDao: DomainDao,
    private val deeplinkDao: DeeplinkDao,
    private val authorization: AuthorizationService,
    private val passwordEncoder: PasswordEncoder,
    private val props: LikaProperties,
    private val transactionHandler: TransactionHandler
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(DeeplinkService::class.java)

    companion object {
        // All days at 1 am
        const val CRON_SCHEDULER = "0 0 1 * * *"
        /** Creator of the links of the configuration */
        const val SYSTEM_CREATOR = "system"
        private val BCRYPT_HASH = Regex("^\\$2[aby]?\\$\\d{2}\\$.{53}$")
    }

    @Scheduled(cron = CRON_SCHEDULER, zone = "Europe/Paris")
    fun cleanExpiredDaily() {
        deeplinkDao.removeAllExpired()
    }

    @CacheEvict("deeplinks", allEntries = true)
    fun init() {
        cleanExpiredDaily()
        hashPlaintextPasswords()
        props.defaultLinks?.forEach { l ->
            val domain = domainDao.findStandardByHost(props.domains!![l.domainIndex].host)!!
            if (deeplinkDao.findByHostAndSlug(domain.host, l.slug) != null) {
                return@forEach
            }
            transactionHandler.runInTransaction {
                val password = l.password?.let { PasswordProtectedOptionsDto(it) }
                save(DeeplinkCreator(slug = l.slug, type = DeeplinkType.read(l.type), targetUrl = l.targetUrl, domainId = domain.id!!, options = OptionsDto(password = password)), domain, SYSTEM_CREATOR, null)
            }
        }
    }

    /**
     * Lika < 0.5.0 stored link passwords in clear text.
     */
    private fun hashPlaintextPasswords() {
        deeplinkDao.findAll()
            .filter { it.options.password?.passwordHash?.matches(BCRYPT_HASH) == false }
            .forEach {
                logger.info("Hashing legacy clear-text password of link {}", it.id)
                it.options.password = PasswordProtectedOptions(passwordEncoder.encode(it.options.password!!.passwordHash))
                deeplinkDao.save(it)
            }
    }

    @Transactional
    @CacheEvict("deeplinks", allEntries = true)
    fun save(l: DeeplinkCreator, caller: LikaPrincipal): Deeplink {
        val domain = authorization.assertCan(caller, l.domainId, DomainRole.EDITOR)
        val existing = if (l.id.isNullOrBlank()) null else deeplinkDao.findOne(l.id!!) ?: throw PermalinkNotFoundException("no link with id ${l.id}")
        if (existing != null) {
            // Moving or editing a link also requires managing its current domain
            try {
                authorization.assertCanHost(caller, existing.host, DomainRole.EDITOR)
            } catch (e: InvalidDomainException) {
                throw PermalinkNotFoundException("link ${existing.id} is on ${existing.host}, which ${caller.name} (${caller.id}) cannot edit")
            }
        }
        return save(l, domain, caller.id, existing)
    }

    private fun save(l: DeeplinkCreator, domain: Domain, callerId: String, existing: Deeplink?): Deeplink {
        if (domain.landingBaseUrl.isNullOrBlank() && l.type == DeeplinkType.WITH_LANDING) {
            throw DomainLandingUnsupportedException()
        }
        val slug = when {
            existing == null && l.slug.isNullOrBlank() -> findValidSlug(domain.host)
            existing != null && l.slug.isNullOrBlank() -> existing.slug
            else -> l.slug
        }
        if (existing == null || existing.host != domain.host || existing.slug != slug) {
            assertSlugAvailable(domain.host, slug)
        }
        val now = ZonedDateTime.now()
        val pl = Deeplink(
            existing?.id ?: UUID.randomUUID().toString(),
            domain.host,
            existing?.createdBy ?: callerId,
            slug,
            "${domain.apiBaseUrl}/${slug}",
            l.targetUrl,
            l.type,
            createdDateTime = existing?.createdDateTime ?: now,
            lastUpdateDateTime = now
        )
        pl.metadata = l.metadata
        if (l.type == DeeplinkType.WITH_LANDING && l.options.landing?.templateId != null) {
            pl.options.landing = LandingOptions(l.options.landing!!.templateId)
        }
        if (l.options.expiry?.expiryDate != null) {
            pl.options.expiry = ExpiryOptions(LocalDate.parse(l.options.expiry!!.expiryDate!!))
        }
        if (l.options.activation?.activationInstant != null) {
            pl.options.activation = ActivationOptions(ZonedDateTime.parse(l.options.activation!!.activationInstant!!),
                l.options.activation!!.targetUrlBeforeActivation)
        }
        pl.options.password = resolvePassword(l.options.password, existing)
        return deeplinkDao.save(pl)
    }

    private fun resolvePassword(dto: PasswordProtectedOptionsDto?, existing: Deeplink?): PasswordProtectedOptions? {
        if (dto == null) {
            // Omitted: keep the current password, if any
            return existing?.options?.password
        }
        if (dto.remove) {
            return null
        }
        if (dto.password.isNullOrBlank()) {
            throw PasswordMissingException()
        }
        return PasswordProtectedOptions(passwordEncoder.encode(dto.password))
    }

    fun checkPassword(deeplink: Deeplink, password: String?): Boolean {
        val hash = deeplink.options.password?.passwordHash ?: return true
        return !password.isNullOrEmpty() && passwordEncoder.matches(password, hash)
    }

    @Cacheable("deeplinks")
    fun findOneByHostAndSlug(host: String, slug: String): Deeplink {
        return deeplinkDao.findByHostAndSlug(host, slug) ?: throw PermalinkNotFoundException(
            domainDao.findByHost(host)?.let { "no link /$slug on domain $host (id ${it.id})" }
                ?: "no domain is registered for host $host (looking for /$slug)"
        )
    }

    @Transactional
    fun findOneByDomainAndSlug(caller: LikaPrincipal, domainId: String, slug: String): Deeplink {
        val domain = authorization.assertCan(caller, domainId, DomainRole.VIEWER)
        return deeplinkDao.findByHostAndSlug(domain.host, slug) ?: throw PermalinkNotFoundException("no link /$slug on domain ${domain.host} (id ${domain.id})")
    }

    @Transactional
    fun findOne(caller: LikaPrincipal, id: String): Deeplink {
        val link = deeplinkDao.findOne(id) ?: throw PermalinkNotFoundException("no link with id $id")
        try {
            authorization.assertCanHost(caller, link.host, DomainRole.VIEWER)
        } catch (e: InvalidDomainException) {
            throw PermalinkNotFoundException("link $id is on ${link.host}, which ${caller.name} (${caller.id}) cannot read")
        }
        return link
    }

    @Transactional
    fun getAll(caller: LikaPrincipal, domainId: String): List<Deeplink> {
        val domain = authorization.assertCan(caller, domainId, DomainRole.VIEWER)
        return deeplinkDao.findAllForHost(domain.host)
    }

    /**
     * Without [domainId], lists the links of every domain the caller can access.
     */
    @Transactional
    fun getPage(caller: LikaPrincipal, domainId: String?, req: PageRequest): PageResponse<Deeplink> {
        val hosts = if (domainId.isNullOrBlank()) {
            authorization.accessibleDomains(caller).map { it.host }
        } else {
            listOf(authorization.assertCan(caller, domainId, DomainRole.VIEWER).host)
        }
        return deeplinkDao.findPageForHosts(hosts, req)
    }

    @Transactional
    @CacheEvict("deeplinks", allEntries = true)
    fun deleteOne(caller: LikaPrincipal, id: String) {
        val link = deeplinkDao.findOne(id) ?: throw PermalinkNotFoundException("no link with id $id")
        try {
            authorization.assertCanHost(caller, link.host, DomainRole.EDITOR)
        } catch (e: InvalidDomainException) {
            throw PermalinkNotFoundException("link $id is on ${link.host}, which ${caller.name} (${caller.id}) cannot edit")
        }
        deeplinkDao.deleteOne(link.id)
    }

    @Transactional
    @CacheEvict("deeplinks", allEntries = true)
    fun deleteBySlug(caller: LikaPrincipal, domainId: String, slug: String) {
        val domain = authorization.assertCan(caller, domainId, DomainRole.EDITOR)
        deeplinkDao.findByHostAndSlug(domain.host, slug) ?: throw PermalinkNotFoundException("no link /$slug on domain ${domain.host} (id ${domain.id})")
        deeplinkDao.deleteBySlug(domain.host, slug)
    }

    @Transactional
    fun assertSlugAvailable(caller: LikaPrincipal, domainId: String, slug: String) {
        val domain = authorization.assertCan(caller, domainId, DomainRole.VIEWER)
        assertSlugAvailable(domain.host, slug)
    }

    @Transactional
    fun suggestSlugs(caller: LikaPrincipal, domainId: String, searchStr: String): List<String> {
        val domain = authorization.assertCan(caller, domainId, DomainRole.VIEWER)
        val normalizedString = StringUtils.normalizeAlphaNumeric(searchStr)
        deeplinkDao.findByHostAndSlug(domain.host, normalizedString) ?: return listOf(normalizedString)
        val alternatives = HashSet<String>()
        do {
            alternatives.addAll(
                StringUtils.suggestAlternatives(normalizedString, 10)
                    .filter { a -> deeplinkDao.findByHostAndSlug(domain.host, a) == null })
        } while (alternatives.size < 4)
        return alternatives.toList().subList(0, 4)
    }

    @Transactional
    fun findValidSlug(host: String): String {
        val slugs = StringUtils.generateSlugs()
        for (s in slugs) {
            deeplinkDao.findByHostAndSlug(host, s) ?: return s
        }
        return findValidSlug(host)
    }

    fun assertSlugAvailable(host: String, slug: String?) {
        if (slug.isNullOrBlank()) {
            return
        }
        if (isForbidden(slug)) {
            throw SlugAlreadyExistsException()
        }
        if (deeplinkDao.findByHostAndSlug(host, slug) != null) {
            throw SlugAlreadyExistsException()
        }
    }
}
