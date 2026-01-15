package li.lika.common.exceptions

class InvalidPasswordException: MessageAndErrorCodeException(401, ERROR_CODE, "Please provide a valid password") {
    companion object {
        const val ERROR_CODE = "resource-access-denied-wrong-password"
    }
}
