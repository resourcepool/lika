package li.lika.common.exceptions

/** Unknown, expired or revoked token, key or password */
class InvalidCredentialsException(message: String = "Invalid credentials") : MessageAndErrorCodeException(401, ERROR_CODE, message) {
    companion object {
        const val ERROR_CODE = "invalid-credentials"
    }
}

/** The rights of the user changed since the token was issued: renew it */
class TokenStaleException : MessageAndErrorCodeException(401, ERROR_CODE, "The rights of this user changed: renew the token") {
    companion object {
        const val ERROR_CODE = "token-stale"
    }
}

class UserDisabledException : MessageAndErrorCodeException(403, ERROR_CODE, "This user is disabled") {
    companion object {
        const val ERROR_CODE = "user-disabled"
    }
}

class UserNotFoundException : MessageAndErrorCodeException(404, ERROR_CODE, "User not found") {
    companion object {
        const val ERROR_CODE = "user-not-found"
    }
}

class UserAlreadyExistsException : MessageAndErrorCodeException(409, ERROR_CODE, "A user with this email already exists") {
    companion object {
        const val ERROR_CODE = "user-already-exists"
    }
}

class LastAdminException : MessageAndErrorCodeException(409, ERROR_CODE, "Lika needs at least one active admin") {
    companion object {
        const val ERROR_CODE = "last-admin"
    }
}

/** Invitation or password reset link which is unknown, already used or expired */
class InvalidLinkTokenException : MessageAndErrorCodeException(400, ERROR_CODE, "This link is invalid or has expired") {
    companion object {
        const val ERROR_CODE = "invalid-link"
    }
}

class SetupException(message: String, errorCode: String, status: Int) : MessageAndErrorCodeException(status, errorCode, message) {
    companion object {
        fun alreadyDone() = SetupException("Lika already has an admin", "setup-done", 409)
        fun invalidToken() = SetupException("Invalid setup token: copy it from the API logs", "invalid-setup-token", 403)
    }
}

class WeakPasswordException : MessageAndErrorCodeException(400, ERROR_CODE, "Passwords need at least $MIN_LENGTH characters") {
    companion object {
        const val ERROR_CODE = "weak-password"
        const val MIN_LENGTH = 10
    }
}

class TooManyAttemptsException : MessageAndErrorCodeException(429, ERROR_CODE, "Too many failed attempts. Try again in a few minutes.") {
    companion object {
        const val ERROR_CODE = "too-many-attempts"
    }
}

class ApiKeyNotFoundException : MessageAndErrorCodeException(404, ERROR_CODE, "API key not found") {
    companion object {
        const val ERROR_CODE = "api-key-not-found"
    }
}

/** The operation is not available with the configured identity provider */
class UnsupportedByProviderException(message: String) : MessageAndErrorCodeException(400, ERROR_CODE, message) {
    companion object {
        const val ERROR_CODE = "unsupported-by-provider"
    }
}

/** The current password given to change it is wrong. Not a 401: the session itself is fine. */
class WrongPasswordException : MessageAndErrorCodeException(400, ERROR_CODE, "Wrong current password") {
    companion object {
        const val ERROR_CODE = "wrong-password"
    }
}

/** Signed in with the identity provider, but never invited to Lika */
class UserNotInvitedException : MessageAndErrorCodeException(403, ERROR_CODE, "This account has no access to Lika: ask an admin for an invitation") {
    companion object {
        const val ERROR_CODE = "not-invited"
    }
}

/** The identity provider refused or failed a request */
class IdentityProviderException(message: String, reason: Throwable? = null) : MessageAndErrorCodeException(502, ERROR_CODE, message, reason) {
    companion object {
        const val ERROR_CODE = "identity-provider-error"
    }
}
