package li.lika.common.persistence

import li.lika.common.models.Domain

interface DomainDao {
    fun findOne(id: String): Domain?
    fun deleteOne(id: String): Boolean
    fun findByHost(host: String): Domain?
    fun findStandardByHost(host: String): Domain?
    fun save(h: Domain): Domain
    fun findAll(): List<Domain>

    /**
     * Warning! This is voluntarily synchronous
     */
    fun deleteAll()
}
