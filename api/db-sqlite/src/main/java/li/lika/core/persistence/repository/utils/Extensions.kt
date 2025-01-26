package li.lika.core.persistence.repository.utils

import li.lika.common.models.Permalink
import li.lika.common.models.dtos.*
import li.lika.core.persistence.models.PermalinkDb
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort.Direction

fun SortDirection.toSpringDirection(): Direction {
    return if (this == SortDirection.DESC) Direction.DESC else Direction.ASC
}

fun Sort.toSpringSort(): org.springframework.data.domain.Sort {
    return org.springframework.data.domain.Sort.by(
        direction.toSpringDirection(),
        property.toValidDBProperty()
    )
}

fun SortProperty.toValidDBProperty(): String {
    return when(this) {
        SortProperty.LAST_ACTIVITY -> "lastUpdateDateTime"
        SortProperty.SLUG -> "slug"
        SortProperty.CREATION_DATE -> "createdDateTime"
    }
}

fun <T, U> Page<U>.toPageResponse(req: PageRequest, transform: (U) -> T): PageResponse<T> {
    return PageResponse(req, totalElements.toInt(), toList().map(transform))
}
