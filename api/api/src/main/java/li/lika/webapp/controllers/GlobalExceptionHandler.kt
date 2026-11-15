package li.lika.webapp.controllers

import li.lika.common.exceptions.*
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import jakarta.servlet.http.HttpServletRequest
import li.lika.webapp.support.RequestHost
import org.springframework.web.servlet.ModelAndView

@ControllerAdvice
class GlobalExceptionHandler(private val requestHost: RequestHost) {

    private val logger = KotlinLogging.logger {}

    /**
     * Why a link or host was not found, with the headers the domain came from. Debug level, under a logger name
     * an environment variable can enable alone: LOGGING_LEVEL_LI_LIKA_NOTFOUND=DEBUG
     */
    private val notFoundLogger = KotlinLogging.logger(NOT_FOUND_LOGGER)

    companion object {
        const val NOT_FOUND_LOGGER = "li.lika.notfound"
    }

    @ExceptionHandler(MessageAndErrorCodeException::class)
    fun handleMessageAndErrorCodeException(ex: MessageAndErrorCodeException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("MessageAndErrorCodeException occurred: ${ex.message}", ex)
        return ResponseEntity
            .status(ex.statusCode)
            .body(ex.res())
    }

    @ExceptionHandler(DomainAlreadyExistsException::class)
    fun handleDomainAlreadyExistsException(ex: DomainAlreadyExistsException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("Domain already exists: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ex.res())
    }

    @ExceptionHandler(DomainLandingUnsupportedException::class)
    fun handleDomainLandingUnsupportedException(ex: DomainLandingUnsupportedException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("Domain landing unsupported: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ex.res())
    }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbiddenException(ex: ForbiddenException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("Access forbidden: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ex.res())
    }

    @ExceptionHandler(InvalidPasswordException::class)
    fun handleInvalidPasswordException(ex: InvalidPasswordException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("Access denied wrong password: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ex.res())
    }

    @ExceptionHandler(InvalidDomainException::class)
    fun handleInvalidDomainException(ex: InvalidDomainException, request: HttpServletRequest): Any {
        logger.warn("Invalid domain: ${ex.message}")
        if (request.getHeader("Accept")?.contains("application/json") == true) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ex.res())
        }
        return ModelAndView("forward:/static/error.html")
    }

    @ExceptionHandler(InvalidHostException::class)
    fun handleInvalidHostException(ex: InvalidHostException, request: HttpServletRequest): Any {
        notFoundLogger.debug { "Invalid host: ${ex.message} [${requestHost.describe(request)}]" }
        if (request.getHeader("Accept")?.contains("application/json") == true) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ex.res())
        }
        return ModelAndView("forward:/static/error.html")
    }

    @ExceptionHandler(PermalinkNotFoundException::class)
    fun handlePermalinkNotFoundException(ex: PermalinkNotFoundException, request: HttpServletRequest): Any {
        notFoundLogger.debug { "Link not found: ${ex.detail ?: ex.message} [${requestHost.describe(request)}]" }
        if (request.getHeader("Accept")?.contains("application/json") == true) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ex.res())
        }
        return ModelAndView("forward:/static/error.html")
    }

    @ExceptionHandler(SlugAlreadyExistsException::class)
    fun handleSlugAlreadyExistsException(ex: SlugAlreadyExistsException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("Slug already exists: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ex.res())
    }

    @ExceptionHandler(PasswordMissingException::class)
    fun handlePasswordMissing(ex: PasswordMissingException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("Password missing: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ex.res())
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: AuthenticationException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("Authentication failed: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(MessageAndErrorCodeResponse(401, "authentication-failed", "Authentication failed"))
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("Access denied: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(MessageAndErrorCodeResponse(403, "access-denied", "Access denied"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        val errors = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        logger.warn("Validation failed: $errors")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(MessageAndErrorCodeResponse(400, "validation-failed", "Validation failed: ${errors.joinToString(", ")}"))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("Illegal argument: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(MessageAndErrorCodeResponse(400, "illegal-argument", ex.message ?: "Illegal argument provided"))
    }
}
