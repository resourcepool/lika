package li.lika.webapp.controllers

import jakarta.servlet.http.HttpServletRequest
import li.lika.common.exceptions.InvalidPasswordException
import li.lika.webapp.support.RequestHost
import li.lika.common.models.Deeplink
import li.lika.common.models.hideTarget
import li.lika.common.models.isPasswordProtected
import li.lika.core.services.DeeplinkService
import li.lika.common.models.DomainRole
import li.lika.core.services.AuthorizationService
import li.lika.common.security.LikaPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * Read-only lookups, available to viewers.
 */
@RequestMapping("/api/search")
@RestController
class ApiSearchController(
    private val deeplinkService: DeeplinkService,
    private val authorization: AuthorizationService,
    private val requestHost: RequestHost
) {

    @GetMapping("/slug/suggestions/{searchStr}")
    fun getSlugSuggestions(@PathVariable searchStr: String, @RequestParam domainId: String, @AuthenticationPrincipal p: LikaPrincipal): List<String> {
        return deeplinkService.suggestSlugs(p, domainId, searchStr)
    }

    /**
     * The target of a password-protected link is only returned with the right password.
     */
    @GetMapping("/slug/{slug}")
    fun search(@PathVariable slug: String,
               @RequestParam(required = false) domainId: String?,
               @RequestParam(required = false) password: String?,
               request: HttpServletRequest,
               @AuthenticationPrincipal p: LikaPrincipal): Deeplink {
        val caller = p
        val d = domainId ?: authorization.assertCanHost(caller, requestHost.of(request) ?: "", DomainRole.VIEWER).id!!
        val deepLink = deeplinkService.findOneByDomainAndSlug(caller, d, slug)
        if (deepLink.isPasswordProtected()) {
            if (password.isNullOrBlank()) {
                return deepLink.hideTarget()
            }
            if (!deeplinkService.checkPassword(deepLink, password)) {
                throw InvalidPasswordException()
            }
        }
        return deepLink
    }
}
