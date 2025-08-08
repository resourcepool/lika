package li.lika.core.persistence.repository;

import li.lika.core.persistence.models.PermalinkDb;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PermalinkRepository extends CrudRepository<PermalinkDb, String>, PagingAndSortingRepository<PermalinkDb, String> {
    Optional<PermalinkDb> findFirstByHostAndSlug(String host, String slug);
    List<PermalinkDb> findAllByHostOrderByLastUpdateDateTimeDesc(String host);
    Page<PermalinkDb> findAllByHostOrderByLastUpdateDateTimeDesc(String host, Pageable pageable);
    List<PermalinkDb> deleteAllByOptionExpiryDateIsBefore(LocalDate date);
    void deleteAllByHostAndSlug(String host, String slug);

    Page<PermalinkDb> findAllByHostAndSearchStringLikeIgnoreCase(String host, String searchString, Pageable pageable);
}
