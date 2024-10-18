package li.lika.common.exceptions

class InvalidHostException: MessageAndErrorCodeException(400, ERROR_CODE, "Invalid host. Please choose a valid host") {
    companion object {
        const val ERROR_CODE = "invalid-host"
    }
}
