package li.lika.webapp.controllers

import li.lika.common.exceptions.ForbiddenException
import li.lika.common.exceptions.InvalidDomainException
import li.lika.common.exceptions.PermalinkNotFoundException
import li.lika.common.models.Deeplink
import li.lika.common.models.DeeplinkCreator
import li.lika.common.models.dtos.*
import li.lika.core.services.DomainService
import li.lika.core.services.DeeplinkService
import li.lika.security.models.AccessTokenAuthentication
import li.lika.security.models.security.Roles
import li.lika.security.models.security.utils.Claims
import li.lika.security.models.security.utils.RoleUtils
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RequestMapping("/api/links")
@RestController
class ApiLinksController(private val deeplinkService: DeeplinkService, private val domainService: DomainService) {

    @GetMapping("/sync")
    fun getAll(@RequestParam domainId: String, @AuthenticationPrincipal p: Principal): List<Deeplink> {
        val owner = p as AccessTokenAuthentication
        assertResourceReadAuthorized(owner, domainId)
        return deeplinkService.getAll(owner.getId(), domainId)
    }

    @GetMapping
    fun getPage(@RequestParam domainId: String, @RequestParam params: Map<String, Any>, @AuthenticationPrincipal p: Principal): PageResponse<Deeplink> {
        val owner = p as AccessTokenAuthentication
        assertResourceReadAuthorized(owner, domainId)
        val req = PageRequest.parseParams(params)
        return deeplinkService.getPage(owner.getId(), domainId, req)
    }

    @GetMapping("{id}")
    fun getOneById(@RequestParam domainId: String, @PathVariable id: String, @AuthenticationPrincipal p: Principal): Deeplink {
        val owner = p as AccessTokenAuthentication
        assertResourceReadAuthorized(owner, domainId)
        return deeplinkService.findOne(id)?: throw PermalinkNotFoundException()
    }

    @GetMapping("slug/{slug}")
    fun assertSlugAvailable(@PathVariable slug: String, @RequestParam domainId: String, @AuthenticationPrincipal p: Principal) {
        val owner = p as AccessTokenAuthentication
        assertResourceReadAuthorized(owner, domainId)
        val domain = domainService.findOneByDomainIdAndOwnerId(domainId, owner.getId())?: throw InvalidDomainException()
        return deeplinkService.assertSlugAvailable(domain.host, slug)
    }

    @PostMapping
    fun create(@RequestBody pl: DeeplinkCreator, @AuthenticationPrincipal p: Principal): Deeplink {
        val owner = p as AccessTokenAuthentication
        assertResourceWriteAuthorized(owner, pl.domainId)
        return deeplinkService.save(pl, owner.getId())
    }

    @PutMapping
    fun edit(@RequestBody pl: DeeplinkCreator, @AuthenticationPrincipal p: Principal): Deeplink {
        val owner = p as AccessTokenAuthentication
        assertResourceWriteAuthorized(owner, pl.domainId)
        return deeplinkService.save(pl, owner.getId())
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String, @AuthenticationPrincipal p: Principal) {
        val owner = p as AccessTokenAuthentication
        val pl = deeplinkService.findOne(id)?: throw PermalinkNotFoundException()
        assertResourceWriteAuthorized(owner, pl.ownerId)
        return deeplinkService.deleteOne(pl.id)
    }

    @DeleteMapping("/slug/{slug}")
    fun delete(@PathVariable slug: String, @RequestParam domainId: String, @AuthenticationPrincipal p: Principal) {
        val owner = p as AccessTokenAuthentication
        assertResourceWriteAuthorized(owner, domainId)
        val domain = domainService.findOneByDomainIdAndOwnerId(domainId, owner.getId())?: throw InvalidDomainException()
        return deeplinkService.deleteBySlug(domain.host, slug)
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
