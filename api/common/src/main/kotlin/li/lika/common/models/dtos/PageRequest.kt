package li.lika.common.models.dtos

data class PageRequest(val limit: Int, val offset: Int, val filter: String?, val sort: Sort?) {
    companion object
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
