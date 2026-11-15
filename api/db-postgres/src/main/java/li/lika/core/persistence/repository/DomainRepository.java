package li.lika.core.persistence.repository;

import li.lika.core.persistence.models.DomainDb;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DomainRepository extends CrudRepository<DomainDb, String> {

    Optional<DomainDb> findFirstByHostAndOwnerIdIsNullAndStandardIsTrue(String host);

    @Query("select u from DomainDb u where u.host = ?1")
    Optional<DomainDb> findFirstByHost(@NotNull String string);
}
