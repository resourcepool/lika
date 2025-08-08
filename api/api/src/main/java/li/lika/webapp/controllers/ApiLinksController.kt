package li.lika.webapp.controllers

import li.lika.common.exceptions.ForbiddenException
import li.lika.common.exceptions.InvalidDomainException
import li.lika.common.exceptions.PermalinkNotFoundException
import li.lika.common.models.Permalink
import li.lika.common.models.PermalinkCreator
import li.lika.common.models.dtos.*
import li.lika.core.services.DomainService
import li.lika.core.services.PermalinkService
import li.lika.security.models.AccessTokenAuthentication
import li.lika.security.models.security.Roles
import li.lika.security.models.security.utils.Claims
import li.lika.security.models.security.utils.RoleUtils
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RequestMapping("/api/links")
@RestController
class ApiLinksController(private val permalinkService: PermalinkService, private val domainService: DomainService) {

    @GetMapping("/sync")
    fun getAll(@RequestParam domainId: String, @AuthenticationPrincipal p: Principal): List<Permalink> {
        val owner = p as AccessTokenAuthentication
        assertResourceReadAuthorized(owner, domainId)
        return permalinkService.getAll(owner.getId(), domainId)
    }

    @GetMapping
    fun getPage(@RequestParam domainId: String, @RequestParam params: Map<String, Any>, @AuthenticationPrincipal p: Principal): PageResponse<Permalink> {
        val owner = p as AccessTokenAuthentication
        assertResourceReadAuthorized(owner, domainId)
        val req = PageRequest.parseParams(params)
        return permalinkService.getPage(owner.getId(), domainId, req)
    }

    @GetMapping("slug/{slug}")
    fun assertSlugAvailable(@PathVariable slug: String, @RequestParam domainId: String, @AuthenticationPrincipal p: Principal) {
        val owner = p as AccessTokenAuthentication
        assertResourceReadAuthorized(owner, domainId)
        val domain = domainService.findOneByDomainIdAndOwnerId(domainId, owner.getId())?: throw InvalidDomainException()
        return permalinkService.assertSlugAvailable(domain.host, slug)
    }

    @PostMapping
    fun create(@RequestBody pl: PermalinkCreator, @AuthenticationPrincipal p: Principal): Permalink {
        val owner = p as AccessTokenAuthentication
        assertResourceWriteAuthorized(owner, pl.domainId)
        return permalinkService.save(pl, owner.getId())
    }

    @PutMapping
    fun edit(@RequestBody pl: PermalinkCreator, @AuthenticationPrincipal p: Principal): Permalink {
        val owner = p as AccessTokenAuthentication
        assertResourceWriteAuthorized(owner, pl.domainId)
        return permalinkService.save(pl, owner.getId())
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
        val domain = domainService.findOneByDomainIdAndOwnerId(domainId, owner.getId())?: throw InvalidDomainException()
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

fun PageRequest.Companion.parseParams(params: Map<String, Any>): PageRequest {
    val limit = 50.coerceAtMost(params["limit"]?.toString()?.toIntOrNull() ?: 20)
    val offset = 0.coerceAtLeast(params["offset"]?.toString()?.toIntOrNull()?:0)
    val filter = params["filter"]?.toString()
    val property = SortProperty.parse(params["sort_by"]?.toString())
    val direction = SortDirection.parse(params["sort_direction"]?.toString())
    return PageRequest(limit, offset, filter, Sort.with(direction, property))
}
