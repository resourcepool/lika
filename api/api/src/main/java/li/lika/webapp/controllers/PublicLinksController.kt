package li.lika.webapp.controllers

import li.lika.common.exceptions.InvalidHostException
import li.lika.common.exceptions.PermalinkNotFoundException
import li.lika.common.models.Deeplink
import li.lika.common.models.DeeplinkType
import li.lika.common.models.currentTargetUrl
import li.lika.common.models.isPasswordProtected
import li.lika.core.services.DomainService
import li.lika.core.services.DeeplinkService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView


@RequestMapping
@Controller
class PublicLinksController(
        private val domainService: DomainService,
        private val deeplinkService: DeeplinkService) {
    private val logger = org.slf4j.LoggerFactory.getLogger(PublicLinksController::class.java)

    @GetMapping("{rootDeeplink}")
    fun getDeeplink(@RequestHeader(name="Host", required = false) host: String? = null,
                    @RequestHeader(name="X-Forwarded-Host", required = false) forwardedHost: String? = null,
                    @PathVariable rootDeeplink: String): ModelAndView {

        val host = sanitizeHost(host, forwardedHost)
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

        val pl = findResolvable(host, rootDeeplink)
        if (pl.type == DeeplinkType.SIMPLE) {
            if (pl.isPasswordProtected()) {
                return protectedPage(pl, false)
            }
            return ModelAndView("redirect:${pl.currentTargetUrl()}")
        }
        // Redirect to Landing page
        val domain = domainService.findOneByHost(pl.host) ?: throw InvalidHostException()
        return ModelAndView("redirect:${domain.landingBaseUrl}?l=${pl.slug}")
    }

    /**
     * Password form of a protected link: redirects to the target if the password matches.
     */
    @PostMapping("{rootDeeplink}")
    fun unlockDeeplink(@RequestHeader(name="Host", required = false) host: String? = null,
                       @RequestHeader(name="X-Forwarded-Host", required = false) forwardedHost: String? = null,
                       @PathVariable rootDeeplink: String,
                       @RequestParam(required = false) password: String?): ModelAndView {
        val pl = findResolvable(sanitizeHost(host, forwardedHost), rootDeeplink)
        if (pl.type != DeeplinkType.SIMPLE) {
            throw PermalinkNotFoundException()
        }
        if (!deeplinkService.checkPassword(pl, password)) {
            return protectedPage(pl, true)
        }
        return ModelAndView("redirect:${pl.currentTargetUrl()}")
    }

    /**
     * Expired links, and links not active yet without a fallback URL, are not found.
     */
    private fun findResolvable(host: String?, slug: String): Deeplink {
        val pl = deeplinkService.findOneByHostAndSlug(host ?: throw InvalidHostException(), slug)
        pl.currentTargetUrl() ?: throw PermalinkNotFoundException()
        return pl
    }

    private fun protectedPage(pl: Deeplink, error: Boolean): ModelAndView {
        val mav = ModelAndView("protected")
            .addObject("l", pl.slug)
            .addObject("error", error)
        if (error) {
            mav.status = HttpStatus.UNAUTHORIZED
        }
        return mav
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
