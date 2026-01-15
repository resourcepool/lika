package li.lika.webapp.controllers

import li.lika.common.exceptions.ForbiddenException
import li.lika.common.exceptions.InvalidDomainException
import li.lika.common.exceptions.InvalidPasswordException
import li.lika.common.models.Deeplink
import li.lika.common.models.DeeplinkType
import li.lika.common.models.hideTarget
import li.lika.common.models.safe
import li.lika.core.services.DomainService
import li.lika.core.services.DeeplinkService
import li.lika.security.models.AccessTokenAuthentication
import li.lika.security.models.security.Roles
import li.lika.security.models.security.utils.Claims
import li.lika.security.models.security.utils.RoleUtils
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RequestMapping("/api/search")
@RestController
class ApiSearchController(
    private val deeplinkService: DeeplinkService,
    private val domainService: DomainService
) {

    @GetMapping("/slug/suggestions/{searchStr}")
    fun getSlugSuggestions(@PathVariable searchStr: String, @RequestParam domainId: String, @AuthenticationPrincipal p: Principal): List<String> {
        val owner = p as AccessTokenAuthentication
        assertResourceAuthorized(owner, domainId)
        return deeplinkService.suggestSlugs(owner.getId(), domainId, searchStr)
    }

    private fun isPasswordProtected(deepLink: Deeplink): Boolean {
        return !(deepLink.options.password?.password.isNullOrBlank())
    }

    @GetMapping("/slug/{slug}")
    fun search(@PathVariable slug: String,
               @RequestParam(required = false) domainId: String?,
               @RequestParam(required = false) password: String?,
               @RequestHeader(name="Host", required = false) host: String?,
               @AuthenticationPrincipal p: Principal): Deeplink {
        val owner = p as AccessTokenAuthentication
        var d = domainId
        if (domainId == null) {
            d = domainService.findOneByHostAndOwnerId(host!!, owner.getId())?.id
            if (d == null) {
                throw InvalidDomainException()
            }
        }
        assertResourceAuthorized(owner, d!!)

        val deepLink = deeplinkService.findOneByOwnerIdAndDomainIdAndSlug(owner.getId(), d, slug)
        if (isPasswordProtected(deepLink)) {
            // Password protected
            if (password.isNullOrBlank()) {
                return deepLink.hideTarget()
            }
            if (deepLink.options.password?.password != password) {
                throw InvalidPasswordException()
            }
        }
        return deepLink.safe()
    }

    fun assertResourceAuthorized(owner: AccessTokenAuthentication, domainId: String) {
        if (!RoleUtils.hasRole(Roles.parse(Claims.ORG_ROLE_READ, owner.getId(), domainId), owner.roles)) {
            throw ForbiddenException()
        }
    }

}
