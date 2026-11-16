package li.lika.core.persistence.repository;

import li.lika.core.persistence.models.DeeplinkDb;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PermalinkRepository extends CrudRepository<DeeplinkDb, String>, PagingAndSortingRepository<DeeplinkDb, String>, JpaSpecificationExecutor<DeeplinkDb> {
    Optional<DeeplinkDb> findFirstByHostAndSlug(String host, String slug);
    List<DeeplinkDb> findAllByHostOrderByLastUpdateDateTimeDesc(String host);
    long countByHost(String host);
    List<DeeplinkDb> deleteAllByOptionExpiryDateIsBefore(LocalDate date);
    void deleteAllByHostAndSlug(String host, String slug);
    void deleteAllByHost(String host);

    Page<DeeplinkDb> findAllByHostIn(Collection<String> hosts, Pageable pageable);
    Page<DeeplinkDb> findAllByHostInAndSearchStringLikeIgnoreCase(Collection<String> hosts, String searchString, Pageable pageable);
}
