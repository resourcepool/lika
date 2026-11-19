package li.lika.core.persistence.repository

import li.lika.core.persistence.models.EngagementDb
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
interface EngagementRepository : CrudRepository<EngagementDb, String> {

    /** @return 0 when the bucket does not exist yet */
    @Transactional
    @Modifying
    @Query("update EngagementDb e set e.visits = e.visits + :visits where e.id = :id")
    fun increment(@Param("id") id: String, @Param("visits") visits: Long): Int

    /** Rows of resolution, start of the period, visits */
    @Query("select e.resolution, e.periodStart, sum(e.visits) from EngagementDb e where e.host in :hosts and e.periodStart >= :from group by e.resolution, e.periodStart")
    fun sumByPeriod(@Param("hosts") hosts: Collection<String>, @Param("from") from: LocalDateTime): List<Array<Any>>

    @Query("select e.resolution, e.periodStart, sum(e.visits) from EngagementDb e where e.linkId = :linkId and e.periodStart >= :from group by e.resolution, e.periodStart")
    fun sumByPeriodForLink(@Param("linkId") linkId: String, @Param("from") from: LocalDateTime): List<Array<Any>>

    /** Rows of host, visits */
    @Query("select e.host, sum(e.visits) from EngagementDb e where e.host in :hosts and e.periodStart >= :from group by e.host")
    fun sumByHost(@Param("hosts") hosts: Collection<String>, @Param("from") from: LocalDateTime): List<Array<Any>>

    /** Rows of link id, visits, most visited first */
    @Query("select e.linkId, sum(e.visits) from EngagementDb e where e.host in :hosts and e.periodStart >= :from group by e.linkId order by sum(e.visits) desc, e.linkId")
    fun topLinks(@Param("hosts") hosts: Collection<String>, @Param("from") from: LocalDateTime, pageable: Pageable): List<Array<Any>>

    @Query("select distinct e.linkId from EngagementDb e where e.host in :hosts")
    fun visitedLinkIds(@Param("hosts") hosts: Collection<String>): List<String>

    @Query("select distinct e.periodStart from EngagementDb e where e.resolution = :resolution and e.periodStart < :before")
    fun periodStarts(@Param("resolution") resolution: String, @Param("before") before: LocalDateTime): List<LocalDateTime>

    @Query("select e from EngagementDb e where e.resolution = :resolution and e.periodStart >= :from and e.periodStart < :to")
    fun findBuckets(@Param("resolution") resolution: String, @Param("from") from: LocalDateTime, @Param("to") to: LocalDateTime): List<EngagementDb>

    @Transactional
    @Modifying
    @Query("delete from EngagementDb e where e.resolution = :resolution and e.periodStart >= :from and e.periodStart < :to")
    fun deleteBuckets(@Param("resolution") resolution: String, @Param("from") from: LocalDateTime, @Param("to") to: LocalDateTime): Int

    @Transactional
    @Modifying
    @Query("delete from EngagementDb e where e.resolution <> :kept and e.periodStart < :before")
    fun deleteBefore(@Param("before") before: LocalDateTime, @Param("kept") kept: String): Int

    @Transactional
    @Modifying
    @Query("delete from EngagementDb e where e.host = :host")
    fun deleteAllForHost(@Param("host") host: String): Int
}
