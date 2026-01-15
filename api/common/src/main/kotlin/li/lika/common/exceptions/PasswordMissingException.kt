package li.lika.common.exceptions

class PasswordMissingException: MessageAndErrorCodeException(400, ERROR_CODE, "Protected links need a password") {
    companion object {
        const val ERROR_CODE = "resource-bad-request-missing-password"
    }
}
