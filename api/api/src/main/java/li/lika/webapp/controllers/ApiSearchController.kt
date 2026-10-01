package li.lika.webapp.controllers

import li.lika.common.exceptions.InvalidPasswordException
import li.lika.common.models.Deeplink
import li.lika.common.models.hideTarget
import li.lika.common.models.isPasswordProtected
import li.lika.core.services.DeeplinkService
import li.lika.core.services.DomainAccessService
import li.lika.security.models.AccessTokenAuthentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.security.Principal

/**
 * Read-only lookups, also available to read tokens.
 */
@RequestMapping("/api/search")
@RestController
class ApiSearchController(
    private val deeplinkService: DeeplinkService,
    private val domainAccess: DomainAccessService
) {

    @GetMapping("/slug/suggestions/{searchStr}")
    fun getSlugSuggestions(@PathVariable searchStr: String, @RequestParam domainId: String, @AuthenticationPrincipal p: Principal): List<String> {
        return deeplinkService.suggestSlugs(p as AccessTokenAuthentication, domainId, searchStr)
    }

    /**
     * The target of a password-protected link is only returned with the right password.
     */
    @GetMapping("/slug/{slug}")
    fun search(@PathVariable slug: String,
               @RequestParam(required = false) domainId: String?,
               @RequestParam(required = false) password: String?,
               @RequestHeader(name="Host", required = false) host: String?,
               @AuthenticationPrincipal p: Principal): Deeplink {
        val caller = p as AccessTokenAuthentication
        val d = domainId ?: domainAccess.assertCanReadHost(caller, host?.trim()?.lowercase() ?: "").id!!
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
