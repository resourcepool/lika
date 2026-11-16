package li.lika.common.models.dtos

data class PageRequest(
    val limit: Int,
    val offset: Int,
    val filter: String?,
    val sort: Sort?,
    /** All must match */
    val metadata: List<MetadataFilter> = emptyList()
) {
    companion object
}

/**
 * A metadata entry a link must have: [key] with [value], or [key] with any value when [value] is null.
 * Keys and values match whole, ignoring case.
 */
data class MetadataFilter(val key: String, val value: String? = null) {
    companion object {
        const val MAX_FILTERS = 10

        /** `key=value`, or `key` alone for any value */
        fun parse(str: String): MetadataFilter? {
            val index = str.indexOf('=')
            val key = (if (index < 0) str else str.substring(0, index)).trim()
            if (key.isEmpty()) return null
            return MetadataFilter(key, if (index < 0) null else str.substring(index + 1))
        }
    }
}

enum class SortDirection(val value: String) {
    ASC("asc"), DESC("desc");



    companion object {
        fun parse(str: String?): SortDirection? {
            if (str.isNullOrBlank()) {
                return null
            }
            return SortDirection.entries.firstOrNull { it.value == str }
        }
    }
}

enum class SortProperty(val value: String) {
    LAST_ACTIVITY("last_activity"),
    CREATION_DATE("creation_date"),
    SLUG("slug");

    companion object {
        fun parse(str: String?): SortProperty? {
            if (str.isNullOrBlank()) {
                return null
            }
            return entries.firstOrNull { it.value == str }
        }
    }
}

data class Sort(val direction: SortDirection, val property: SortProperty) {
    companion object {
        fun with(direction: SortDirection?, property: SortProperty?): Sort {
            return Sort(direction?:SortDirection.DESC, property?:SortProperty.CREATION_DATE)
        }
    }
}
