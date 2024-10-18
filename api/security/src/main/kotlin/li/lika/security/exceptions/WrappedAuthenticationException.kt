package li.lika.security.exceptions

class WrappedAuthenticationException(t: Throwable? = null) : MessageAndErrorCodeSecurityException("security.authentication.failed", "An error occurred during authentication", t)
