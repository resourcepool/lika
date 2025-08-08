package li.lika.common.exceptions

class AccountNotFoundException: MessageAndErrorCodeException(404, ERROR_CODE, "Account not found") {
    companion object {
        const val ERROR_CODE = "account-not-found"
    }
}
