package li.lika.common.exceptions

class AccountAlreadyExistsException: MessageAndErrorCodeException(400, ERROR_CODE, "This account is already registered") {
    companion object {
        const val ERROR_CODE = "account-already-exists"
    }
}
