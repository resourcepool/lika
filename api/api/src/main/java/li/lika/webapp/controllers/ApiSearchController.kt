package li.lika.webapp.controllers

import li.lika.common.models.Permalink
import li.lika.common.exceptions.ForbiddenException
import li.lika.common.exceptions.InvalidDomainException
import li.lika.core.services.DomainService
import li.lika.core.services.PermalinkService
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
        private val permalinkService: PermalinkService,
        private val domainService: DomainService
) {

    @GetMapping("/slug/suggestions/{searchStr}")
    fun getSlugSuggestions(@PathVariable searchStr: String, @RequestParam domainId: String, @AuthenticationPrincipal p: Principal): List<String> {
        val owner = p as AccessTokenAuthentication
        assertResourceAuthorized(owner, domainId)
        return permalinkService.suggestSlugs(owner.getId(), domainId, searchStr)
    }

    @GetMapping("/slug/{slug}")
    fun search(@PathVariable slug: String, @RequestParam(required = false) domainId: String?, @RequestHeader(name="Host", required = false) host: String?, @AuthenticationPrincipal p: Principal): Permalink {
        val owner = p as AccessTokenAuthentication
        var d = domainId
        if (domainId == null) {
            d = domainService.findOneByHostAndOwnerId(host!!, owner.getId())?.id
            if (d == null) {
                throw InvalidDomainException()
            }
        }
        assertResourceAuthorized(owner, d!!)
        return permalinkService.findOneByOwnerIdAndDomainIdAndSlug(owner.getId(), d, slug)
    }

    fun assertResourceAuthorized(owner: AccessTokenAuthentication, domainId: String) {
        if (!RoleUtils.hasRole(Roles.parse(Claims.ORG_ROLE_READ, owner.getId(), domainId), owner.roles)) {
            throw ForbiddenException()
        }
    }

}
