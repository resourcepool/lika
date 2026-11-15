package li.lika.common.persistence

import li.lika.common.models.Account

/**
 * Accounts of Lika < 0.7.0, read once to migrate them to API keys, then deleted.
 */
interface AccountDao {
    fun findAll(): List<Account>
    fun deleteAll()
}
