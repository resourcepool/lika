package li.lika.core.services

import li.lika.common.exceptions.DomainAlreadyExistsException
import li.lika.common.models.Domain
import li.lika.common.models.DomainCreator
import li.lika.common.persistence.DomainDao
import li.lika.core.props.LikaProperties
import li.lika.security.models.AccessTokenAuthentication
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class DomainService(private val domainDao: DomainDao, private val props: LikaProperties) {

    @CacheEvict("domains", allEntries = true)
    fun init() {
        props.domains?.forEach { d ->
            val domain = domainDao.findStandardByHost(d.host)
            if (domain == null) {
                domainDao.save(Domain(d.id, true, null, d.host, d.apiBaseUrl, d.landingBaseUrl))
            }
        }
    }

    @CacheEvict("domains", allEntries = true)
    fun save(d: DomainCreator, ownerId: String): Domain {
        val host = d.host.trim().lowercase()
        val existingDomain = domainDao.findByHostAndOwnerId(host, ownerId)
        if (existingDomain != null) {
            throw DomainAlreadyExistsException()
        }
        return domainDao.save(Domain(null, false, ownerId, host, d.apiBaseUrl.trim().lowercase(), d.landingBaseUrl.trim().lowercase()))
    }

    @CacheEvict("domains", allEntries = true)
    fun saveGlobal(d: DomainCreator, ownerId: String): Domain {
        val host = d.host.trim().lowercase()
        val existingDomain = domainDao.findByHostAndOwnerId(host, ownerId)
        if (existingDomain != null) {
            throw DomainAlreadyExistsException()
        }
        return domainDao.save(Domain(null, true, ownerId, host, d.apiBaseUrl.trim().lowercase(), d.landingBaseUrl.trim().lowercase()))
    }

    @Cacheable("domains")
    fun findById(id: String): Domain? {
        return domainDao.findOne(id)
    }

    @Cacheable("domains")
    fun findOneByHostAndOwnerId(host: String, ownerId: String): Domain? {
        return domainDao.findByHostAndOwnerId(host, ownerId)
    }

    @Cacheable("domains")
    fun findOneByDomainIdAndOwnerId(domainId: String, ownerId: String): Domain? {
        return domainDao.findByDomainIdAndOwnerId(domainId, ownerId)
    }

    fun getAll(owner: AccessTokenAuthentication): List<Domain> {
        return domainDao.findAllByOwnerId(owner.getId())
    }

    @CacheEvict("domains")
    fun deleteByHostAndOwnerId(host: String, owner: AccessTokenAuthentication) {
        return domainDao.deleteByHostAndOwnerId(host, owner.getId())
    }

    @CacheEvict("domains")
    fun deleteOne(id: String) {
        domainDao.deleteOne(id)
    }

    fun findAll(): List<Domain> {
        return domainDao.findAll()
    }

}
