package li.lika.webapp.controllers

import li.lika.common.models.Deeplink
import li.lika.common.models.DeeplinkCreator
import li.lika.common.models.dtos.*
import li.lika.core.services.DeeplinkService
import li.lika.security.models.AccessTokenAuthentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.security.Principal

/**
 * Links of the domains the caller owns or was granted (every domain for a global account).
 * /api/admin/links is kept for compatibility: it serves the same endpoints, restricted to admins.
 */
@RequestMapping("/api/links", "/api/admin/links")
@RestController
class ApiLinksController(private val deeplinkService: DeeplinkService) {

    @GetMapping("/sync")
    fun getAll(@RequestParam domainId: String, @AuthenticationPrincipal p: Principal): List<Deeplink> {
        return deeplinkService.getAll(p as AccessTokenAuthentication, domainId)
    }

    /**
     * Without domainId, lists links across every domain the caller can access.
     */
    @GetMapping
    fun getPage(@RequestParam(required = false) domainId: String?, @RequestParam params: Map<String, Any>, @AuthenticationPrincipal p: Principal): PageResponse<Deeplink> {
        return deeplinkService.getPage(p as AccessTokenAuthentication, domainId, PageRequest.parseParams(params))
    }

    @GetMapping("{id}")
    fun getOneById(@PathVariable id: String, @AuthenticationPrincipal p: Principal): Deeplink {
        return deeplinkService.findOne(p as AccessTokenAuthentication, id)
    }

    @GetMapping("slug/{slug}")
    fun assertSlugAvailable(@PathVariable slug: String, @RequestParam domainId: String, @AuthenticationPrincipal p: Principal) {
        deeplinkService.assertSlugAvailable(p as AccessTokenAuthentication, domainId, slug)
    }

    @PostMapping
    fun create(@RequestBody pl: DeeplinkCreator, @AuthenticationPrincipal p: Principal): Deeplink {
        pl.id = null
        return deeplinkService.save(pl, p as AccessTokenAuthentication)
    }

    @PutMapping
    fun edit(@RequestBody pl: DeeplinkCreator, @AuthenticationPrincipal p: Principal): Deeplink {
        require(!pl.id.isNullOrBlank()) { "Link id is required" }
        return deeplinkService.save(pl, p as AccessTokenAuthentication)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String, @AuthenticationPrincipal p: Principal) {
        deeplinkService.deleteOne(p as AccessTokenAuthentication, id)
    }

    @DeleteMapping("/slug/{slug}")
    fun delete(@PathVariable slug: String, @RequestParam domainId: String, @AuthenticationPrincipal p: Principal) {
        deeplinkService.deleteBySlug(p as AccessTokenAuthentication, domainId, slug)
    }
}

fun PageRequest.Companion.parseParams(params: Map<String, Any>): PageRequest {
    val limit = 50.coerceAtMost(params["limit"]?.toString()?.toIntOrNull() ?: 20).coerceAtLeast(1)
    val offset = 0.coerceAtLeast(params["offset"]?.toString()?.toIntOrNull()?:0)
    val filter = params["filter"]?.toString()
    val property = SortProperty.parse(params["sort_by"]?.toString())
    val direction = SortDirection.parse(params["sort_direction"]?.toString())
    return PageRequest(limit, offset, filter, Sort.with(direction, property))
}
