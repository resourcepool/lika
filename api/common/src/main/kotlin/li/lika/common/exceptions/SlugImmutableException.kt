package li.lika.common.exceptions

class SlugImmutableException: MessageAndErrorCodeException(400, ERROR_CODE, "Slug is immutable. It cannot change. Please remove it and create another one.") {
    companion object {
        const val ERROR_CODE = "slug-cannot-change"
    }
}
