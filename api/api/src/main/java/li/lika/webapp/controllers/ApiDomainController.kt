package li.lika.webapp.controllers

import li.lika.common.models.Domain
import li.lika.core.services.DomainService
import li.lika.security.models.AccessTokenAuthentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RequestMapping("/api/domains")
@RestController
class ApiDomainController(private val domainService: DomainService) {

    /**
     * Domains the caller owns or was granted (every domain for a global account).
     */
    @GetMapping
    fun getAll(@AuthenticationPrincipal p: Principal): List<Domain> {
        return domainService.getAll(p as AccessTokenAuthentication)
    }
}
