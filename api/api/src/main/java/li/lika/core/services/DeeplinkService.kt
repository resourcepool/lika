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
import li.lika.core.props.ServerProperties
import li.lika.core.services.handlers.TransactionHandler
import li.lika.security.models.AccessTokenAuthentication
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*

@Service
class DeeplinkService(
    private val domainDao: DomainDao,
    private val deeplinkDao: DeeplinkDao,
    private val serverProps: ServerProperties,
    private val props: LikaProperties,
    private val transactionHandler: TransactionHandler
) {

    companion object {
        // All days at 1 am
        const val CRON_SCHEDULER = "0 0 1 * * *"
    }

    @Scheduled(cron = CRON_SCHEDULER, zone = "Europe/Paris")
    fun cleanExpiredDaily() {
        deeplinkDao.removeAllExpired()
    }

    @CacheEvict("deeplinks", allEntries = true)
    fun init(token: AccessTokenAuthentication) {
        cleanExpiredDaily()
        props.defaultLinks?.forEach { l ->
            val domain = domainDao.findStandardByHost(props.domains!![l.domainIndex].host)!!
            transactionHandler.runInTransaction {
                val password = when(l.password) {
                    null -> null
                    else -> PasswordProtectedOptionsDto(l.password)
                }
                save(DeeplinkCreator(slug = l.slug, type = DeeplinkType.read(l.type), targetUrl = l.targetUrl, domainId = domain.id!!, options = OptionsDto(password = password) ), token.getId())
            }
        }
    }

    @Transactional
    @CacheEvict("deeplinks", allEntries = true)
    fun save(l: DeeplinkCreator, ownerId: String): Deeplink {
        var id = l.id
        val domain: Domain =
            domainDao.findByDomainIdAndOwnerId(l.domainId, ownerId) ?: throw InvalidHostException()
        if (domain.landingBaseUrl == null && l.type == DeeplinkType.WITH_LANDING) {
            throw DomainLandingUnsupportedException()
        }
        if (l.type == DeeplinkType.PROTECTED && l.options.password?.password.isNullOrBlank()) {
            throw PasswordMissingException()
        }
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            transactionHandler.runInTransaction { assertSlugAvailable(domain.host, l.slug) }
            if (l.slug.isNullOrBlank()) {
                l.slug = transactionHandler.runInTransaction { findValidSlug(domain.host) }
            }
        } else {
            val existingPl = transactionHandler.runInTransaction { assertPermalinkExists(id) }
            if (existingPl.slug != l.slug) {
                throw SlugImmutableException()
            }
        }
        val shortenedLink = "${domain.apiBaseUrl}/${l.slug}"
        val pl = Deeplink(
            id,
            domain.host,
            ownerId,
            l.slug,
            shortenedLink,
            l.targetUrl,
            l.type
        )
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
        if (l.options.password?.password != null) {
            pl.options.password = PasswordProtectedOptions(l.options.password!!.password)
        }
        return deeplinkDao.save(pl)
    }

    @Transactional
    fun assertPermalinkExists(id: String): Deeplink {
        return deeplinkDao.findOne(id) ?: throw PermalinkNotFoundException()
    }

    @Cacheable("deeplinks")
    fun findOneByHostAndSlug(host: String, slug: String): Deeplink {
        return deeplinkDao.findByHostAndSlug(host, slug) ?: throw PermalinkNotFoundException()
    }

    @Cacheable("deeplinks")
    @Transactional
    fun findOneByOwnerIdAndDomainIdAndSlug(ownerId: String, domainId: String, slug: String): Deeplink {
        val domain = domainDao.findByDomainIdAndOwnerId(domainId, ownerId) ?: throw InvalidDomainException()
        return deeplinkDao.findByHostAndSlug(domain.host, slug) ?: throw PermalinkNotFoundException()
    }

    fun getAll(): List<Deeplink> {
        return deeplinkDao.findAll()
    }

    @Transactional
    fun getAll(domainId: String): List<Deeplink> {
        val domain = domainDao.findOne(domainId) ?: throw InvalidDomainException()
        return deeplinkDao.findAllForHost(domain.host)
    }

    @Transactional
    fun getAll(ownerId: String, domainId: String): List<Deeplink> {
        val domain = domainDao.findByDomainIdAndOwnerId(domainId, ownerId) ?: throw InvalidDomainException()
        return deeplinkDao.findAllForHost(domain.host)
    }

    @Transactional
    fun getPage(ownerId: String, domainId: String, req: PageRequest): PageResponse<Deeplink> {
        val domain = domainDao.findByDomainIdAndOwnerId(domainId, ownerId) ?: throw InvalidDomainException()
        return deeplinkDao.findPageForHost(domain.host, req)
    }

    @Transactional
    fun getPageAdmin(domainId: String, req: PageRequest): PageResponse<Deeplink> {
        val domain = domainDao.findOne(domainId) ?: throw InvalidDomainException()
        return deeplinkDao.findPageForHost(domain.host, req)
    }

    @Transactional
    fun deleteBySlug(host: String, slug: String) {
        deeplinkDao.findByHostAndSlug(host, slug) ?: throw PermalinkNotFoundException()
        return deeplinkDao.deleteBySlug(host, slug)
    }

    @Transactional
    fun suggestSlugs(ownerId: String, domainId: String, searchStr: String): List<String> {
        val domain = domainDao.findByDomainIdAndOwnerId(domainId, ownerId) ?: throw InvalidDomainException()
        val normalizedString = StringUtils.normalizeAlphaNumeric(searchStr)
        deeplinkDao.findByHostAndSlug(domain.host, normalizedString) ?: return listOf(normalizedString)
        val alternatives = HashSet<String>()
        do {
            alternatives.addAll(
                StringUtils.suggestAlternatives(normalizedString, 10)
                    .filter { a -> deeplinkDao.findByHostAndSlug(domainId, a) == null })
        } while (alternatives.size < 4)
        return alternatives.toList().subList(0, 4)
    }

    @Transactional
    fun findValidSlug(host: String): String {
        val slugs = StringUtils.generateSlugs()
        for (i in 0..slugs.size) {
            val s = slugs[i]
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

    fun findOne(id: String): Deeplink? {
        return deeplinkDao.findOne(id)
    }

    fun deleteOne(id: String) {
        deeplinkDao.deleteOne(id)
    }
}
