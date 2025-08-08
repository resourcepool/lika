package li.lika.webapp.controllers

import li.lika.common.exceptions.ForbiddenException
import li.lika.common.exceptions.InvalidDomainException
import li.lika.common.exceptions.PermalinkNotFoundException
import li.lika.common.models.Permalink
import li.lika.common.models.PermalinkCreator
import li.lika.common.models.dtos.PageRequest
import li.lika.common.models.dtos.PageResponse
import li.lika.core.services.AccountService
import li.lika.core.services.DomainService
import li.lika.core.services.PermalinkService
import li.lika.security.models.AccessTokenAuthentication
import li.lika.security.models.security.Roles
import li.lika.security.models.security.utils.Claims
import li.lika.security.models.security.utils.RoleUtils
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import javax.security.auth.login.AccountNotFoundException

@RequestMapping("/api/admin/links")
@RestController
class ApiAdminLinksController(private val accountService: AccountService, private val permalinkService: PermalinkService, private val domainService: DomainService) {

    @GetMapping("/sync")
    fun getAll(@RequestParam domainId: String, @AuthenticationPrincipal p: Principal): List<Permalink> {
        val owner = p as AccessTokenAuthentication
        assertResourceReadAuthorized(owner, domainId)
        return permalinkService.getAll(domainId)
    }

    @GetMapping
    fun getPage(@RequestParam domainId: String, @RequestParam params: Map<String, Any>, @AuthenticationPrincipal p: Principal): PageResponse<Permalink> {
        val owner = p as AccessTokenAuthentication
        assertResourceReadAuthorized(owner, domainId)
        val req = PageRequest.Companion.parseParams(params)
        return permalinkService.getPageAdmin(domainId, req)
    }

    @GetMapping("slug/{slug}")
    fun assertSlugAvailable(@PathVariable slug: String, @RequestParam domainId: String, @AuthenticationPrincipal p: Principal) {
        val owner = p as AccessTokenAuthentication
        assertResourceReadAuthorized(owner, domainId)
        val domain = domainService.findById(domainId)?: throw InvalidDomainException()
        return permalinkService.assertSlugAvailable(domain.host, slug)
    }

    @PostMapping
    fun create(@RequestBody pl: PermalinkCreator, @AuthenticationPrincipal p: Principal): Permalink {
        val owner = p as AccessTokenAuthentication
        assertResourceWriteAuthorized(owner, pl.domainId)
        val domain = domainService.findById(pl.domainId)?: throw InvalidDomainException()
        return permalinkService.save(pl, domain.ownerId?:owner.getId())
    }

    @PutMapping
    fun edit(@RequestBody pl: PermalinkCreator, @AuthenticationPrincipal p: Principal): Permalink {
        val owner = p as AccessTokenAuthentication
        assertResourceWriteAuthorized(owner, pl.domainId)
        val domain = domainService.findById(pl.domainId)?: throw InvalidDomainException()
        return permalinkService.save(pl, domain.ownerId!!)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String, @AuthenticationPrincipal p: Principal) {
        val owner = p as AccessTokenAuthentication
        val pl = permalinkService.findOne(id)?: throw PermalinkNotFoundException()
        assertResourceWriteAuthorized(owner, pl.ownerId)
        return permalinkService.deleteOne(pl.id)
    }

    @DeleteMapping("/slug/{slug}")
    fun delete(@PathVariable slug: String, @RequestParam domainId: String, @AuthenticationPrincipal p: Principal) {
        val owner = p as AccessTokenAuthentication
        assertResourceWriteAuthorized(owner, domainId)
        val domain = domainService.findById(domainId)?: throw InvalidDomainException()
        return permalinkService.deleteBySlug(domain.host, slug)
    }


    fun assertResourceReadAuthorized(owner: AccessTokenAuthentication, domainId: String) {
        if (!RoleUtils.hasRole(Roles.parse(Claims.ORG_ROLE_READ, owner.getId(), domainId), owner.roles)) {
            throw ForbiddenException()
        }
    }

    fun assertResourceWriteAuthorized(owner: AccessTokenAuthentication, domainId: String) {
        if (!RoleUtils.hasRole(Roles.parse(Claims.ORG_ROLE_READ_WRITE, owner.getId(), domainId), owner.roles)) {
            throw ForbiddenException()
        }
    }
}
