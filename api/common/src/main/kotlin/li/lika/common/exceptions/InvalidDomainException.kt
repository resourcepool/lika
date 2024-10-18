package li.lika.common.exceptions

class InvalidDomainException: MessageAndErrorCodeException(400, ERROR_CODE, "Invalid domain. Please choose a valid domain") {
    companion object {
        const val ERROR_CODE = "invalid-domain"
    }
}

