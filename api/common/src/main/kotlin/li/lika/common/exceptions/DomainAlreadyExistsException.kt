package li.lika.common.exceptions

class DomainAlreadyExistsException: MessageAndErrorCodeException(400, ERROR_CODE, "This domain is already registered") {
    companion object {
        const val ERROR_CODE = "domain-already-exists"
    }
}
