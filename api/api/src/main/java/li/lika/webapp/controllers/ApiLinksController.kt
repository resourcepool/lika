package li.lika.webapp.controllers

import li.lika.common.models.Deeplink
import li.lika.common.models.DeeplinkCreator
import li.lika.common.models.LinkBatchRequest
import li.lika.common.models.LinkBatchResult
import li.lika.common.models.MetadataKeyValues
import li.lika.common.models.dtos.*
import li.lika.core.services.DeeplinkService
import li.lika.common.security.LikaPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * Links of the domains the caller has a role on (every domain for an admin).
 * /api/admin/links is kept for compatibility: it serves the same endpoints, restricted to admins.
 */
@RequestMapping("/api/links", "/api/admin/links")
@RestController
class ApiLinksController(private val deeplinkService: DeeplinkService) {

    @GetMapping("/sync")
    fun getAll(@RequestParam domainId: String, @AuthenticationPrincipal p: LikaPrincipal): List<Deeplink> {
        return deeplinkService.getAll(p, domainId)
    }

    /**
     * Without domainId, lists links across every domain the caller can access.
     * Each `meta` (repeatable) keeps the links with that metadata: `key=value`, or `key` for any value.
     */
    @GetMapping
    fun getPage(
        @RequestParam(required = false) domainId: String?,
        @RequestParam params: Map<String, Any>,
        @RequestParam(name = "meta", required = false) meta: List<String>?,
        @AuthenticationPrincipal p: LikaPrincipal
    ): PageResponse<Deeplink> {
        return deeplinkService.getPage(p, domainId, PageRequest.parseParams(params, meta))
    }

    /** Metadata keys in use, with their most used values */
    @GetMapping("/metadata")
    fun metadata(@RequestParam(required = false) domainId: String?, @AuthenticationPrincipal p: LikaPrincipal): List<MetadataKeyValues> {
        return deeplinkService.metadataValues(p, domainId)
    }

    /** One operation on several links, all or none */
    @PostMapping("/batch")
    fun batch(@RequestBody req: LinkBatchRequest, @AuthenticationPrincipal p: LikaPrincipal): LinkBatchResult {
        return deeplinkService.batch(p, req)
    }

    @GetMapping("{id}")
    fun getOneById(@PathVariable id: String, @AuthenticationPrincipal p: LikaPrincipal): Deeplink {
        return deeplinkService.findOne(p, id)
    }

    @GetMapping("slug/{slug}")
    fun assertSlugAvailable(@PathVariable slug: String, @RequestParam domainId: String, @AuthenticationPrincipal p: LikaPrincipal) {
        deeplinkService.assertSlugAvailable(p, domainId, slug)
    }

    @PostMapping
    fun create(@RequestBody pl: DeeplinkCreator, @AuthenticationPrincipal p: LikaPrincipal): Deeplink {
        pl.id = null
        return deeplinkService.save(pl, p)
    }

    @PutMapping
    fun edit(@RequestBody pl: DeeplinkCreator, @AuthenticationPrincipal p: LikaPrincipal): Deeplink {
        require(!pl.id.isNullOrBlank()) { "Link id is required" }
        return deeplinkService.save(pl, p)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String, @AuthenticationPrincipal p: LikaPrincipal) {
        deeplinkService.deleteOne(p, id)
    }

    @DeleteMapping("/slug/{slug}")
    fun delete(@PathVariable slug: String, @RequestParam domainId: String, @AuthenticationPrincipal p: LikaPrincipal) {
        deeplinkService.deleteBySlug(p, domainId, slug)
    }
}

fun PageRequest.Companion.parseParams(params: Map<String, Any>, meta: List<String>? = null): PageRequest {
    val limit = 50.coerceAtMost(params["limit"]?.toString()?.toIntOrNull() ?: 20).coerceAtLeast(1)
    val offset = 0.coerceAtLeast(params["offset"]?.toString()?.toIntOrNull()?:0)
    val filter = params["filter"]?.toString()
    val property = SortProperty.parse(params["sort_by"]?.toString())
    val direction = SortDirection.parse(params["sort_direction"]?.toString())
    val metadata = meta.orEmpty().mapNotNull { MetadataFilter.parse(it) }.distinct()
    require(metadata.size <= MetadataFilter.MAX_FILTERS) { "At most ${MetadataFilter.MAX_FILTERS} metadata filters" }
    return PageRequest(limit, offset, filter, Sort.with(direction, property), metadata)
}
