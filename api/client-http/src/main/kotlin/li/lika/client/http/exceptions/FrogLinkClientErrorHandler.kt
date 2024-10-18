package li.lika.client.http.exceptions

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import li.lika.common.exceptions.MessageAndErrorCodeException
import li.lika.common.exceptions.MessageAndErrorCodeResponse
import li.lika.common.exceptions.res
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.reactive.function.client.WebClientResponseException

@RestControllerAdvice
class FrogLinkClientErrorHandler(private val mapper: ObjectMapper) {

    @ExceptionHandler(MessageAndErrorCodeException::class)
    fun handleCustomException(ce: MessageAndErrorCodeException): ResponseEntity<MessageAndErrorCodeResponse> {
        return ResponseEntity(ce.res(), HttpStatus.resolve(ce.statusCode)!!)
    }

    fun parseWebClientResponseException(t: WebClientResponseException): RuntimeException {
        val tree = mapper.readTree(t.responseBodyAsByteArray)
        if (tree.isObject) {
            tree as ObjectNode
            if (tree.has("errorCode")) {
                return MessageAndErrorCodeException.parse(t.statusCode.value(), tree.get("errorCode").asText())
            }
        }
        return IllegalStateException(t.message)
    }
}
