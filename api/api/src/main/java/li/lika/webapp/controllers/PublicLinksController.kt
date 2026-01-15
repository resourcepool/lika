package li.lika.webapp.controllers

import li.lika.common.exceptions.InvalidHostException
import li.lika.common.models.DeeplinkType
import li.lika.core.services.DomainService
import li.lika.core.services.DeeplinkService
import li.lika.security.props.SecurityProperties
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
    private val properties: SecurityProperties,
        private val domainService: DomainService,
        private val deeplinkService: DeeplinkService) {
    private val logger = org.slf4j.LoggerFactory.getLogger(PublicLinksController::class.java)

    @GetMapping("{rootDeeplink}")
    fun getDeeplink(model: ModelMap,
                    @RequestHeader(name="Host", required = false) host: String? = null,
                    @RequestHeader(name="X-Forwarded-Host", required = false) forwardedHost: String? = null,
                    @PathVariable rootDeeplink: String): ModelAndView {

        var host = sanitizeHost(host, forwardedHost)
        if (rootDeeplink == "index.html") {
            logger.warn("Root deeplink is blank, redirecting to home")
            if (host != null) {
                val domain = domainService.findOneByHost(host)
                if (domain?.landingBaseUrl?.isNotBlank() == true) {
                    return ModelAndView("redirect:${domain.landingBaseUrl}")
                }
            }
            return ModelAndView("forward:/static/index.html")
        }

        val pl = deeplinkService.findOneByHostAndSlug(host!!, rootDeeplink)
        if (pl.type == DeeplinkType.SIMPLE) {
            return ModelAndView("redirect:${pl.targetUrl}", model)
        }
        if (pl.type == DeeplinkType.PROTECTED) {
            return ModelAndView("protected")
                .addObject("l", pl.slug)
                .addObject("accessToken", properties.defaultGlobalAccessToken)
        }
        val domain = domainService.findOneByHostAndOwnerId(pl.host, pl.ownerId) ?: throw InvalidHostException()
        return ModelAndView("redirect:${domain.landingBaseUrl}?l=${pl.slug}", model)
    }

    private fun sanitizeHost(host: String?, forwardedHost: String?): String? {
        var result = host?.trim()?.lowercase()
        logger.debug("Host: {}", result)
        if (forwardedHost != null) {
            logger.debug("X-Forwarded-Host: $forwardedHost")
            result = forwardedHost.trim().lowercase()
        }
        return result
    }

}
