package li.lika.common.exceptions

/**
 * @param detail why the link was not found, for the logs only: it may name a domain the caller cannot read
 */
class PermalinkNotFoundException(val detail: String? = null): MessageAndErrorCodeException(404, ERROR_CODE, "Permalink not found. Please choose a valid slug") {
    companion object {
        const val ERROR_CODE = "slug-not-found"
    }
}
