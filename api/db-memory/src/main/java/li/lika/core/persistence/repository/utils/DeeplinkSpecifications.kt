package li.lika.core.persistence.repository.utils

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import li.lika.common.models.dtos.MetadataFilter
import li.lika.common.models.dtos.PageRequest
import li.lika.core.persistence.models.DeeplinkDb
import org.springframework.data.jpa.domain.Specification

/**
 * Links of some hosts, matching the text filter and every metadata filter of a page request.
 */
object DeeplinkSpecifications {
    private const val ESCAPE = '!'

    fun matching(hosts: Collection<String>, req: PageRequest, mapper: ObjectMapper): Specification<DeeplinkDb> = Specification { root, _, cb ->
        val predicates = mutableListOf<Predicate>(root.get<String>("host").`in`(hosts))
        if (!req.filter.isNullOrBlank()) {
            predicates += containsIgnoringCase(cb, root.get("searchString"), req.filter!!)
        }
        for (filter in req.metadata) {
            predicates += containsIgnoringCase(cb, root.get("metadata"), jsonFragment(filter, mapper))
        }
        cb.and(*predicates.toTypedArray())
    }

    /**
     * Metadata is stored as a JSON object written by [mapper]: an entry appears in it as `"key":"value"`,
     * written the same way here. Without a value, `"key":` matches any value.
     */
    fun jsonFragment(filter: MetadataFilter, mapper: ObjectMapper): String {
        val key = mapper.writeValueAsString(filter.key) + ":"
        return if (filter.value == null) key else key + mapper.writeValueAsString(filter.value)
    }

    /** The database lowers both sides, so that they agree (SQLite only lowers ASCII letters) */
    private fun containsIgnoringCase(cb: CriteriaBuilder, column: Expression<String>, text: String): Predicate =
        cb.like(cb.lower(column), cb.lower(cb.literal("%" + escape(text) + "%")), ESCAPE)

    private fun escape(value: String) = value
        .replace("$ESCAPE", "$ESCAPE$ESCAPE")
        .replace("%", "$ESCAPE%")
        .replace("_", "${ESCAPE}_")
}
