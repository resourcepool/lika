package li.lika.common.exceptions

class DomainLandingUnsupportedException: MessageAndErrorCodeException(400, ERROR_CODE, "This domain cannot accept permalinks with landing") {
    companion object {
        const val ERROR_CODE = "domain-doesnt-support-landing-permalinks"
    }
}
