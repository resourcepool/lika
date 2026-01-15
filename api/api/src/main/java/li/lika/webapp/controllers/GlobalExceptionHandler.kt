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
import org.springframework.web.servlet.ModelAndView

@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = KotlinLogging.logger {}

    @ExceptionHandler(MessageAndErrorCodeException::class)
    fun handleMessageAndErrorCodeException(ex: MessageAndErrorCodeException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("MessageAndErrorCodeException occurred: ${ex.message}", ex)
        return ResponseEntity
            .status(ex.statusCode)
            .body(ex.res())
    }

    @ExceptionHandler(AccountNotFoundException::class)
    fun handleAccountNotFoundException(ex: AccountNotFoundException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("Account not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ex.res())
    }

    @ExceptionHandler(AccountAlreadyExistsException::class)
    fun handleAccountAlreadyExistsException(ex: AccountAlreadyExistsException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("Account already exists: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
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
        logger.warn("Invalid host: ${ex.message}")
        if (request.getHeader("Accept")?.contains("application/json") == true) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ex.res())
        }
        return ModelAndView("forward:/static/error.html")
    }

    @ExceptionHandler(PermalinkNotFoundException::class)
    fun handlePermalinkNotFoundException(ex: PermalinkNotFoundException, request: HttpServletRequest): Any {
        logger.warn("Permalink not found: ${ex.message}")
        if (request.getHeader("Accept")?.contains("application/json") == true) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ex.res())
        }
        return ModelAndView("forward:/static/error.html")
    }

    @ExceptionHandler(PermanentSlugException::class)
    fun handlePermanentSlugException(ex: PermanentSlugException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("Permanent slug exception: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ex.res())
    }

    @ExceptionHandler(SlugAlreadyExistsException::class)
    fun handleSlugAlreadyExistsException(ex: SlugAlreadyExistsException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("Slug already exists: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ex.res())
    }

    @ExceptionHandler(SlugImmutableException::class)
    fun handleSlugImmutableException(ex: SlugImmutableException, request: HttpServletRequest): ResponseEntity<MessageAndErrorCodeResponse> {
        logger.warn("Slug is immutable: ${ex.message}")
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
