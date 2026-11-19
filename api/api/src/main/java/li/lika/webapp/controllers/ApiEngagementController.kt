package li.lika.webapp.controllers

import li.lika.common.models.EngagementRange
import li.lika.common.models.EngagementReport
import li.lika.common.security.LikaPrincipal
import li.lika.core.services.EngagementService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Visits of the short links, for viewers of their domain.
 */
@RequestMapping("/api/engagement")
@RestController
class ApiEngagementController(private val engagement: EngagementService) {

    /**
     * Visits per period, with the most visited links and the visits per domain.
     * With linkId, the visits of that link. With domainId, of the links of that domain. Otherwise, of every domain
     * the caller reads.
     * @param range 24h (per hour), 7d or 30d (per day, the default), 90d or 12m (per week)
     */
    @GetMapping
    fun report(
        @RequestParam(required = false) domainId: String?,
        @RequestParam(required = false) linkId: String?,
        @RequestParam(required = false) range: String?,
        @AuthenticationPrincipal p: LikaPrincipal
    ): EngagementReport = engagement.report(p, domainId, linkId, EngagementRange.parse(range))
}
