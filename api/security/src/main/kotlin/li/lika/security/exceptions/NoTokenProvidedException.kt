package li.lika.security.exceptions

import org.springframework.security.access.AccessDeniedException

class NoTokenProvidedException: AccessDeniedException("No id token provided. Please provide a valid Authorization header") {
    companion object {
        const val ERROR_CODE = "security.missing.id.token"
    }
}
