package li.lika.webapp.controllers

import li.lika.common.exceptions.InvalidDomainException
import li.lika.common.models.Domain
import li.lika.common.models.DomainCreator
import li.lika.common.models.DomainUpdate
import li.lika.core.services.DomainService
import li.lika.security.models.AccessTokenAuthentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RequestMapping("/api/admin/domains")
@RestController
class ApiAdminDomainController(private val domainService: DomainService) {

    @GetMapping("/{id}")
    fun findOne(@PathVariable id: String): Domain {
        return domainService.findById(id) ?: throw InvalidDomainException()
    }

    @GetMapping
    fun findAll(): List<Domain> {
        return domainService.findAll()
    }

    /**
     * @param ownerId owning account, defaults to the caller
     * @param global marks the domain as a standard (system-wide) domain
     */
    @PostMapping
    fun create(@RequestBody req: DomainCreator, @RequestParam(required = false) ownerId: String? = null, @RequestParam(required = false) global: Boolean? = null, @AuthenticationPrincipal p: Principal): Domain {
        val caller = p as AccessTokenAuthentication
        return domainService.create(req, ownerId ?: caller.getId(), global == true)
    }

    /**
     * Updates the API and landing base URLs. The host cannot change.
     */
    @PatchMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody req: DomainUpdate): Domain {
        return domainService.update(id, req)
    }

    /**
     * Fails with 409 if the domain still has links, unless force=true, which deletes them too.
     */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String, @RequestParam(defaultValue = "false") force: Boolean) {
        domainService.deleteOne(id, force)
    }
}
