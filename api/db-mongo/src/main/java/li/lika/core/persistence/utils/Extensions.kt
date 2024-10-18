package li.lika.core.persistence.utils

import li.lika.common.models.dtos.PageRequest
import li.lika.common.models.dtos.PageResponse
import li.lika.common.models.dtos.Sort
import li.lika.common.models.dtos.SortDirection
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort.Direction

fun SortDirection.toSpringDirection(): Direction {
    return if (this == SortDirection.DESC) Direction.DESC else Direction.ASC
}

fun Sort.toSpringSort(): org.springframework.data.domain.Sort {
    return org.springframework.data.domain.Sort.by(
        direction.toSpringDirection(),
        property.value
    )
}

fun <T, U> Page<U>.toPageResponse(req: PageRequest, transform: (U) -> T): PageResponse<T> {
    return PageResponse(req, totalElements.toInt(), toList().map(transform))
}
