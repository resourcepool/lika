package li.lika.core.persistence

import li.lika.common.models.Account
import li.lika.common.persistence.AccountDao
import li.lika.core.persistence.repository.AccountRepository
import org.springframework.stereotype.Service

@Service
class AccountDaoH2Impl(private val repository: AccountRepository) : AccountDao {

    override fun findAll(): List<Account> {
        return repository.findAll().map { it.toDomain() }
    }

    override fun deleteAll() {
        repository.deleteAll()
    }
}
