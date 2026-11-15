package li.lika.core.persistence

import li.lika.common.models.Account
import li.lika.common.persistence.AccountDao
import li.lika.core.persistence.models.AccountDb
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Repository

@Repository
class AccountDaoMongoImpl(private val template: MongoTemplate) : AccountDao {

    override fun findAll(): List<Account> {
        return template.findAll(AccountDb::class.java).map { it.toDomain() }
    }

    override fun deleteAll() {
        template.remove(AccountDb::class.java).all()
    }
}
