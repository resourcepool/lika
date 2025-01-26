package li.lika.core.persistence.repository;

import li.lika.core.persistence.models.DomainDb;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DomainRepository extends CrudRepository<DomainDb, String> {

    Optional<DomainDb> findFirstByHostAndOwnerIdIsNullAndStandardIsTrue(String host);

    List<DomainDb> findAllByOwnerIdOrStandardIsTrue(String ownerId);

    List<DomainDb> deleteAllByHostAndOwnerId(String host, String ownerId);

    @Query("select u from DomainDb u where u.host = ?1 and (u.ownerId is null or u.ownerId = ?2)")
    Optional<DomainDb> findByHostAndOwnerIdNullOr(String host, String ownerId);

    @Query("select u from DomainDb u where u.id = ?1 and (u.ownerId is null or u.ownerId = ?2)")
    Optional<DomainDb> findByIdAndOwnerIdNullOr(String id, String ownerId);
}
