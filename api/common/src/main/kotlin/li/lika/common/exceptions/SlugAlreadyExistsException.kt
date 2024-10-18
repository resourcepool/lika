package li.lika.common.exceptions

class SlugAlreadyExistsException: MessageAndErrorCodeException(400, ERROR_CODE, "This slug already exists. Please choose another") {
    companion object {
        const val ERROR_CODE = "slug-already-exists"
    }
}
