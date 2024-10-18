package li.lika.client.http.exceptions

import li.lika.common.exceptions.*

fun MessageAndErrorCodeException.Companion.parse(status: Int, errorCode: String): MessageAndErrorCodeException {
    return when (errorCode) {
        AccountAlreadyExistsException.ERROR_CODE -> AccountAlreadyExistsException()
        DomainAlreadyExistsException.ERROR_CODE -> DomainAlreadyExistsException()
        ForbiddenException.ERROR_CODE -> ForbiddenException()
        InvalidDomainException.ERROR_CODE -> InvalidDomainException()
        InvalidHostException.ERROR_CODE -> InvalidHostException()
        PermalinkNotFoundException.ERROR_CODE -> PermalinkNotFoundException()
        PermanentSlugException.ERROR_CODE -> PermanentSlugException()
        SlugAlreadyExistsException.ERROR_CODE -> SlugAlreadyExistsException()
        SlugImmutableException.ERROR_CODE -> SlugImmutableException()
        DomainLandingUnsupportedException.ERROR_CODE -> DomainLandingUnsupportedException()
        else -> MessageAndErrorCodeException(status, UNKNOWN_ERROR, "Internal server error")
    }
}
