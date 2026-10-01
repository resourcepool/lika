package li.lika.common.exceptions

class DomainNotEmptyException(linkCount: Int): MessageAndErrorCodeException(409, ERROR_CODE, "This domain still has $linkCount link(s). Use force=true to delete them along with the domain") {
    companion object {
        const val ERROR_CODE = "domain-not-empty"
    }
}
