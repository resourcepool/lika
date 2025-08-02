package li.lika.webapp.controllers

import li.lika.common.exceptions.InvalidHostException
import li.lika.common.models.PermalinkType
import li.lika.core.services.DomainService
import li.lika.core.services.PermalinkService
import org.springframework.stereotype.Controller
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView


@RequestMapping
@Controller
class PublicLinksController(
        private val domainService: DomainService,
        private val permalinkService: PermalinkService) {

    @GetMapping("{rootPermalink}")
    fun getPermalink(model: ModelMap, @RequestHeader(name="Host", required = false) host: String, @PathVariable rootPermalink: String): ModelAndView {
        val pl = permalinkService.findOneByHostAndSlug(host.trim().toLowerCase(), rootPermalink)
        if (pl.type == PermalinkType.SIMPLE) {
            return ModelAndView("redirect:${pl.targetUrl}", model)
        }
        val domain = domainService.findOneByHostAndOwnerId(pl.host, pl.ownerId) ?: throw InvalidHostException()
        return ModelAndView("redirect:${domain.landingBaseUrl}?l=${pl.slug}", model)
    }

}
