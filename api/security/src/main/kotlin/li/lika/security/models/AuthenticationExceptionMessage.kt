package li.lika.security.models

data class AuthenticationExceptionMessage(val statusCode: Int, val message: String, val errorCode: String? = null)
