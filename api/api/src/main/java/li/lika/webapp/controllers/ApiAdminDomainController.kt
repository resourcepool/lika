package li.lika.webapp.controllers

import li.lika.common.models.Domain
import li.lika.common.models.DomainCreator
import li.lika.core.services.DomainService
import li.lika.security.models.AccessTokenAuthentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RequestMapping("/api/admin/domains")
@RestController
class ApiAdminDomainController(private val domainService: DomainService) {

    @GetMapping("/{id}")
    fun findOne(@PathVariable id: String): Domain? {
        return domainService.findById(id)
    }

    @GetMapping
    fun findAll(): List<Domain> {
        return domainService.findAll()
    }

    @PostMapping
    fun create(@RequestBody req: DomainCreator, @RequestParam(required = false) ownerId: String? = null, @AuthenticationPrincipal p: Principal): Domain {
        val owner = p as AccessTokenAuthentication
        return domainService.save(req, ownerId ?: owner.getId())
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String, @AuthenticationPrincipal p: Principal) {
        val owner = p as AccessTokenAuthentication
        domainService.deleteOne(id)
    }
}
