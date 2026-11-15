package li.lika.core.persistence.repository;

import li.lika.core.persistence.models.AccountDb;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends CrudRepository<AccountDb, String> {
}
