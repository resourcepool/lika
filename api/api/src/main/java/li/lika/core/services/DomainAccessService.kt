package li.lika.core.services

import li.lika.common.exceptions.ForbiddenException
import li.lika.common.exceptions.InvalidDomainException
import li.lika.common.models.Account
import li.lika.common.models.Domain
import li.lika.common.persistence.DomainDao
import li.lika.security.models.AccessTokenAuthentication
import org.springframework.stereotype.Service

/**
 * Single place deciding which domains a caller may read or manage.
 *
 * A global account accesses every domain. Any other account accesses the domains it owns
 * and the domains it was granted. Its read-write token may manage their links, its read token may only read them.
 */
@Service
class DomainAccessService(private val domainDao: DomainDao) {

    fun canAccess(account: Account, domain: Domain): Boolean {
        return account.global || domain.ownerId == account.id || domain.id in account.domainIds
    }

    fun accessibleDomains(caller: AccessTokenAuthentication): List<Domain> {
        return domainDao.findAll().filter { canAccess(caller.account, it) }
    }

    fun assertCanRead(caller: AccessTokenAuthentication, domainId: String): Domain {
        val domain = domainDao.findOne(domainId) ?: throw InvalidDomainException()
        if (!canAccess(caller.account, domain)) {
            // Same error as an unknown domain, to avoid disclosing other accounts' domains
            throw InvalidDomainException()
        }
        return domain
    }

    fun assertCanReadHost(caller: AccessTokenAuthentication, host: String): Domain {
        val domain = domainDao.findByHost(host) ?: throw InvalidDomainException()
        if (!canAccess(caller.account, domain)) {
            throw InvalidDomainException()
        }
        return domain
    }

    fun assertCanWrite(caller: AccessTokenAuthentication, domainId: String): Domain {
        assertReadWrite(caller)
        return assertCanRead(caller, domainId)
    }

    fun assertCanWriteHost(caller: AccessTokenAuthentication, host: String): Domain {
        assertReadWrite(caller)
        return assertCanReadHost(caller, host)
    }

    private fun assertReadWrite(caller: AccessTokenAuthentication) {
        if (!caller.readWrite) {
            throw ForbiddenException()
        }
    }
}
