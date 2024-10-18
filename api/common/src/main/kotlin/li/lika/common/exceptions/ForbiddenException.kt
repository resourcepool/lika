package li.lika.common.exceptions

class ForbiddenException: MessageAndErrorCodeException(403, ERROR_CODE, "You cannot access this resource") {
    companion object {
        const val ERROR_CODE = "resource-access-forbidden"
    }
}
