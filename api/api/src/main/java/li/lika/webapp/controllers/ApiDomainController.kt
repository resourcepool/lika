package li.lika.webapp.controllers

import li.lika.common.exceptions.ForbiddenException
import li.lika.common.models.Domain
import li.lika.common.models.DomainCreator
import li.lika.core.services.DomainService
import li.lika.security.models.AccessTokenAuthentication
import li.lika.security.models.security.Roles
import li.lika.security.models.security.utils.Claims
import li.lika.security.models.security.utils.RoleUtils
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RequestMapping("/api/domains")
@RestController
class ApiDomainController(private val domainService: DomainService) {

    @GetMapping
    fun getAll(@AuthenticationPrincipal p: Principal): List<Domain> {
        val owner = p as AccessTokenAuthentication
        assertResourceAuthorized(owner, Claims.Scope.ALL)
        return domainService.getAll(owner)
    }

    fun assertResourceAuthorized(owner: AccessTokenAuthentication, domainId: String) {
        if (!RoleUtils.hasRole(Roles.parse(Claims.ORG_ROLE_READ, owner.getId(), domainId), owner.roles)) {
            throw ForbiddenException()
        }
    }
}
