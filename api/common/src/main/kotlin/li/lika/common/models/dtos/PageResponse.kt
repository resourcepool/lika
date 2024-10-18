package li.lika.common.models.dtos

data class PageResponse<T>(val request: PageRequest, val totalItemCount: Int, val items: List<T>) {
    fun getPageSize(): Int {
        return items.size
    }

    fun getStartIndex(): Int {
        return request.offset
    }

    fun hasNext(): Boolean {
        return (request.offset + getPageSize()) < totalItemCount
    }
}
