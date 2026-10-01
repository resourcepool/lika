package li.lika.common.persistence

import li.lika.common.models.Account

interface AccountDao {
    fun findAll(): List<Account>
    fun findById(id: String): Account?
    fun findByName(name: String): Account?
    fun findByAccessToken(accessToken: String): Account?

    /**
     * Accounts saved before domain grants existed (Lika < 0.5.0).
     * Saving an account marks it as migrated.
     */
    fun findAllPendingGrantsMigration(): List<Account>
    fun save(o: Account): Account
    fun deleteOne(id: String): Boolean

    /**
     * Warning! This is voluntarily synchronous
     */
    fun deleteAll()
}
