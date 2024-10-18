package li.lika.common.exceptions

data class MessageAndErrorCodeResponse(val statusCode: Int, val errorCode: String, val message: String) {

}

fun MessageAndErrorCodeException.res(): MessageAndErrorCodeResponse {
    return MessageAndErrorCodeResponse(statusCode, errorCode, message)
}
