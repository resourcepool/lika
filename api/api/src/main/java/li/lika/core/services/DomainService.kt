package li.lika.core.services

import li.lika.common.exceptions.DomainAlreadyExistsException
import li.lika.common.exceptions.DomainNotEmptyException
import li.lika.common.exceptions.InvalidDomainException
import li.lika.common.models.Domain
import li.lika.common.models.DomainCreator
import li.lika.common.models.DomainUpdate
import li.lika.common.persistence.DeeplinkDao
import li.lika.common.persistence.DomainDao
import li.lika.core.props.LikaProperties
import li.lika.common.security.LikaPrincipal
import li.lika.core.services.auth.ApiKeyService
import li.lika.core.services.auth.UserService
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DomainService(
    private val domainDao: DomainDao,
    private val deeplinkDao: DeeplinkDao,
    private val authorization: AuthorizationService,
    private val users: UserService,
    private val keys: ApiKeyService,
    private val props: LikaProperties
) {

    @CacheEvict("domains", allEntries = true)
    fun init() {
        props.domains?.forEach { d ->
            val domain = domainDao.findStandardByHost(d.host)
            if (domain == null) {
                domainDao.save(Domain(d.id, true, null, d.host, d.apiBaseUrl, d.landingBaseUrl))
            }
        }
    }

    /**
     * @param standard system-wide domain, shown as such in the admin
     */
    @CacheEvict("domains", allEntries = true)
    fun create(d: DomainCreator, standard: Boolean = false): Domain {
        val host = d.host.trim().lowercase()
        require(host.isNotEmpty()) { "Domain host is required" }
        if (domainDao.findByHost(host) != null) {
            throw DomainAlreadyExistsException()
        }
        return domainDao.save(Domain(null, standard, null, host, d.apiBaseUrl.trim().lowercase(), d.landingBaseUrl.trim().lowercase().ifEmpty { null }))
    }

    /**
     * The host cannot change: published short links depend on it.
     */
    @Transactional
    @CacheEvict(cacheNames = ["domains", "deeplinks"], allEntries = true)
    fun update(id: String, req: DomainUpdate): Domain {
        val domain = domainDao.findOne(id) ?: throw InvalidDomainException()
        val apiBaseUrl = req.apiBaseUrl?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: domain.apiBaseUrl
        val landingBaseUrl = if (req.landingBaseUrl == null) domain.landingBaseUrl else req.landingBaseUrl!!.trim().lowercase().ifEmpty { null }
        val updated = domainDao.save(domain.copy(apiBaseUrl = apiBaseUrl, landingBaseUrl = landingBaseUrl))
        if (apiBaseUrl != domain.apiBaseUrl) {
            // Short URLs are stored on each link
            deeplinkDao.findAllForHost(domain.host).forEach {
                deeplinkDao.save(it.copy(shortenedUrl = "$apiBaseUrl/${it.slug}"))
            }
        }
        return updated
    }

    @Cacheable("domains")
    fun findById(id: String): Domain? {
        return domainDao.findOne(id)
    }

    @Cacheable("domains")
    fun findOneByHost(host: String): Domain? {
        return domainDao.findByHost(host)
    }

    fun getAll(caller: LikaPrincipal): List<Domain> {
        return authorization.accessibleDomains(caller)
    }

    /**
     * Refuses to delete a domain that still has links, unless [force] is set, in which case its links are deleted too.
     * Roles of users and keys on the domain are always removed.
     */
    @Transactional
    @CacheEvict(cacheNames = ["domains", "deeplinks"], allEntries = true)
    fun deleteOne(id: String, force: Boolean) {
        val domain = domainDao.findOne(id) ?: throw InvalidDomainException()
        val linkCount = deeplinkDao.countForHost(domain.host)
        if (linkCount > 0 && !force) {
            throw DomainNotEmptyException(linkCount)
        }
        if (linkCount > 0) {
            deeplinkDao.deleteAllForHost(domain.host)
        }
        users.removeDomain(id)
        keys.removeDomain(id)
        domainDao.deleteOne(id)
    }

    fun findAll(): List<Domain> {
        return domainDao.findAll()
    }

}
