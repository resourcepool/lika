package li.lika.common.exceptions


open class MessageAndErrorCodeException(val statusCode: Int, val errorCode: String, override val message: String, val reason: Throwable? = null): RuntimeException(message, reason) {
    companion object {
        const val UNKNOWN_ERROR = "internal.server.error"
    }
}
