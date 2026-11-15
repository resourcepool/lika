package li.lika.webapp.controllers

import li.lika.common.models.Domain
import li.lika.core.services.DomainService
import li.lika.common.security.LikaPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RequestMapping("/api/domains")
@RestController
class ApiDomainController(private val domainService: DomainService) {

    /**
     * Domains the caller has a role on (every domain for an admin).
     */
    @GetMapping
    fun getAll(@AuthenticationPrincipal p: LikaPrincipal): List<Domain> {
        return domainService.getAll(p)
    }
}
