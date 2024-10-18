package li.lika.core.services

import jakarta.annotation.PostConstruct
import li.lika.common.exceptions.*
import li.lika.common.models.Domain
import li.lika.common.models.Permalink
import li.lika.common.models.PermalinkType
import li.lika.common.models.ProtectedLinks
import li.lika.common.models.PermalinkCreator
import li.lika.common.models.dtos.PageRequest
import li.lika.common.models.dtos.PageResponse
import li.lika.common.persistence.DomainDao
import li.lika.common.persistence.PermalinkDao
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
class PermalinkService(
    private val domainDao: DomainDao,
    private val permalinkDao: PermalinkDao,
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
        permalinkDao.removeAllExpired()
    }

    @CacheEvict("permalinks", allEntries = true)
    fun init(token: AccessTokenAuthentication) {
        cleanExpiredDaily()
        props.defaultLinks?.forEach { l ->
            val domain = domainDao.findStandardByHost(props.domains!![l.domainIndex].host)!!
            transactionHandler.runInTransaction { save(PermalinkCreator(slug = l.slug, targetUrl = l.targetUrl, domainId = domain.id!!, ), token) }
        }
    }

    @Transactional
    @CacheEvict("permalinks", allEntries = true)
    fun save(l: PermalinkCreator, owner: AccessTokenAuthentication): Permalink {
        var id = l.id
        val domain: Domain =
            domainDao.findByDomainIdAndOwnerId(l.domainId, owner.getId()) ?: throw InvalidHostException()
        if (domain.landingBaseUrl == null && l.type == PermalinkType.WITH_LANDING) {
            throw DomainLandingUnsupportedException()
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
        val pl = Permalink(
            id,
            domain.host,
            owner.getId(),
            l.slug,
            shortenedLink,
            l.targetUrl,
            l.type
        )
        if (l.expiryDate != null) {
            pl.expiryDate = LocalDate.parse(l.expiryDate)
        }
        if (l.activationDateTime != null) {
            pl.activationDateTime = ZonedDateTime.parse(l.activationDateTime)
        }
        pl.template = l.template
        pl.customTemplate = l.customTemplate
        return permalinkDao.save(pl)
    }

    @Transactional
    fun assertPermalinkExists(id: String): Permalink {
        return permalinkDao.findOne(id) ?: throw PermalinkNotFoundException()
    }

    @Cacheable("permalinks")
    fun findOneByHostAndSlug(host: String, slug: String): Permalink {
        return permalinkDao.findByHostAndSlug(host, slug) ?: throw PermalinkNotFoundException()
    }

    @Cacheable("permalinks")
    @Transactional
    fun findOneByOwnerIdAndDomainIdAndSlug(ownerId: String, domainId: String, slug: String): Permalink {
        val domain = domainDao.findByDomainIdAndOwnerId(domainId, ownerId) ?: throw InvalidDomainException()
        return permalinkDao.findByHostAndSlug(domain.host, slug) ?: throw PermalinkNotFoundException()
    }

    fun getAll(): List<Permalink> {
        return permalinkDao.findAll()
    }

    @Transactional
    fun getAll(ownerId: String, domainId: String): List<Permalink> {
        val domain = domainDao.findByDomainIdAndOwnerId(domainId, ownerId) ?: throw InvalidDomainException()
        return permalinkDao.findAllForHost(domain.host)
    }

    @Transactional
    fun getPage(ownerId: String, domainId: String, req: PageRequest): PageResponse<Permalink> {
        val domain = domainDao.findByDomainIdAndOwnerId(domainId, ownerId) ?: throw InvalidDomainException()
        return permalinkDao.findPageForHost(domain.host, req)
    }

    @Transactional
    fun deleteBySlug(host: String, slug: String) {
        if (ProtectedLinks.isProtected(slug)) {
            throw PermanentSlugException()
        }
        permalinkDao.findByHostAndSlug(host, slug) ?: throw PermalinkNotFoundException()
        return permalinkDao.deleteBySlug(host, slug)
    }

    @Transactional
    fun suggestSlugs(ownerId: String, domainId: String, searchStr: String): List<String> {
        val domain = domainDao.findByDomainIdAndOwnerId(domainId, ownerId) ?: throw InvalidDomainException()
        val normalizedString = StringUtils.normalizeAlphaNumeric(searchStr)
        permalinkDao.findByHostAndSlug(domain.host, normalizedString) ?: return listOf(normalizedString)
        val alternatives = HashSet<String>()
        do {
            alternatives.addAll(
                StringUtils.suggestAlternatives(normalizedString, 10)
                    .filter { a -> permalinkDao.findByHostAndSlug(domainId, a) == null })
        } while (alternatives.size < 4)
        return alternatives.toList().subList(0, 4)
    }

    @Transactional
    fun findValidSlug(host: String): String {
        val slugs = StringUtils.generateSlugs()
        for (i in 0..slugs.size) {
            val s = slugs[i]
            permalinkDao.findByHostAndSlug(host, s) ?: return s
        }
        return findValidSlug(host)
    }


    fun assertSlugAvailable(host: String, slug: String?) {
        if (slug.isNullOrBlank()) {
            return
        }
        if (permalinkDao.findByHostAndSlug(host, slug) != null) {
            throw SlugAlreadyExistsException()
        }
    }

    fun findOne(id: String): Permalink? {
        return permalinkDao.findOne(id)
    }

    fun deleteOne(id: String) {
        permalinkDao.deleteOne(id)
    }
}
