package li.lika.common.exceptions

class LastGlobalAccountException: MessageAndErrorCodeException(409, ERROR_CODE, "Cannot delete the last global account") {
    companion object {
        const val ERROR_CODE = "last-global-account"
    }
}
