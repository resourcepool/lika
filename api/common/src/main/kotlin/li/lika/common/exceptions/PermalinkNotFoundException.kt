package li.lika.common.exceptions

class PermalinkNotFoundException: MessageAndErrorCodeException(404, ERROR_CODE, "Permalink not found. Please choose a valid slug") {
    companion object {
        const val ERROR_CODE = "slug-not-found"
    }
}
