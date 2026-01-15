package li.lika.core.persistence.repository;

import li.lika.core.persistence.models.DeeplinkDb;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PermalinkRepository extends CrudRepository<DeeplinkDb, String>, PagingAndSortingRepository<DeeplinkDb, String> {
    Optional<DeeplinkDb> findFirstByHostAndSlug(String host, String slug);
    List<DeeplinkDb> findAllByHostOrderByLastUpdateDateTimeDesc(String host);
    Page<DeeplinkDb> findAllByHostOrderByLastUpdateDateTimeDesc(String host, Pageable pageable);
    List<DeeplinkDb> deleteAllByOptionExpiryDateIsBefore(LocalDate date);
    void deleteAllByHostAndSlug(String host, String slug);

    Page<DeeplinkDb> findAllByHostAndSearchStringLikeIgnoreCase(String host, String searchString, Pageable pageable);
}
