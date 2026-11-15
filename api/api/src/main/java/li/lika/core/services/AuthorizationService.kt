package li.lika.core.services

import li.lika.common.exceptions.ForbiddenException
import li.lika.common.exceptions.InvalidDomainException
import li.lika.common.models.Domain
import li.lika.common.models.DomainRole
import li.lika.common.persistence.DomainDao
import li.lika.common.security.LikaPrincipal
import org.springframework.stereotype.Service

/**
 * Single place deciding what a caller may do, from the claims of its token or key.
 *
 * A domain the caller has no role on is reported as unknown, to avoid disclosing it.
 * A domain it can see but not act on as asked is forbidden.
 */
@Service
class AuthorizationService(private val domainDao: DomainDao) {

    fun accessibleDomains(caller: LikaPrincipal, min: DomainRole = DomainRole.VIEWER): List<Domain> {
        return domainDao.findAll().filter { caller.claims.can(it.id!!, min) }
    }

    /** Domain ids the caller manages: every domain for an admin */
    fun managedDomainIds(caller: LikaPrincipal): Set<String> {
        return accessibleDomains(caller, DomainRole.MANAGER).mapNotNull { it.id }.toSet()
    }

    fun assertCan(caller: LikaPrincipal, domainId: String, role: DomainRole): Domain {
        val domain = domainDao.findOne(domainId) ?: throw InvalidDomainException()
        return assertCan(caller, domain, role)
    }

    fun assertCanHost(caller: LikaPrincipal, host: String, role: DomainRole): Domain {
        val domain = domainDao.findByHost(host) ?: throw InvalidDomainException()
        return assertCan(caller, domain, role)
    }

    private fun assertCan(caller: LikaPrincipal, domain: Domain, role: DomainRole): Domain {
        val current = caller.claims.roleOn(domain.id!!) ?: throw InvalidDomainException()
        if (!current.includes(role)) {
            throw ForbiddenException()
        }
        return domain
    }

    fun assertAdmin(caller: LikaPrincipal) {
        if (!caller.admin) {
            throw ForbiddenException()
        }
    }


    fun assertDomainsExist(domainIds: Collection<String>) {
        if (domainIds.any { domainDao.findOne(it) == null }) {
            throw InvalidDomainException()
        }
    }
}
