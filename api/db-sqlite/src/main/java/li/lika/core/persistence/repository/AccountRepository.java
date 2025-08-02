package li.lika.core.persistence.repository;

import li.lika.core.persistence.models.AccountDb;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends CrudRepository<AccountDb, String> {
    Optional<AccountDb> findFirstByName(String name);
    Optional<AccountDb> findFirstByDefaultPublicReadAccessTokenOrDefaultAccessToken(String defaultPublicReadAccessToken, String defaultAccessToken);
}
