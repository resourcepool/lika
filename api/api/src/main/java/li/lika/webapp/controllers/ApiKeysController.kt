package li.lika.webapp.controllers

import li.lika.common.models.*
import li.lika.common.security.LikaPrincipal
import li.lika.core.services.auth.ApiKeyService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * API keys of applications. Admins manage every key, managers the keys of the domains they manage.
 * Secrets are only returned on creation and regeneration.
 */
@RequestMapping("/api/keys")
@RestController
class ApiKeysController(private val keys: ApiKeyService) {

    @GetMapping
    fun list(@AuthenticationPrincipal p: LikaPrincipal, @RequestParam(required = false) domainId: String?): List<ApiKey> = keys.list(p, domainId)

    @GetMapping("/{id}")
    fun get(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String): ApiKey = keys.get(p, id)

    @PostMapping
    fun create(@AuthenticationPrincipal p: LikaPrincipal, @RequestBody req: ApiKeyRequest): ApiKeySecret = keys.create(p, req)

    @PatchMapping("/{id}")
    fun update(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String, @RequestBody req: ApiKeyUpdate): ApiKey = keys.update(p, id, req)

    /** The previous secret stops working immediately */
    @PostMapping("/{id}/regenerate")
    fun regenerate(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String): ApiKeySecret = keys.regenerate(p, id)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String) = keys.delete(p, id)
}
