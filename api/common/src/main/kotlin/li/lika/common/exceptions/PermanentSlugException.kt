package li.lika.common.exceptions

class PermanentSlugException: MessageAndErrorCodeException(400, ERROR_CODE, "Cannot delete a permanent slug") {
    companion object {
        const val ERROR_CODE = "cannot-delete-permanent-slug"
    }
}
