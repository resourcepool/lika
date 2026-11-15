package li.lika.webapp.support

import jakarta.servlet.http.HttpServletRequest
import li.lika.core.props.LikaProperties
import org.springframework.stereotype.Component

/**
 * The domain a request was sent to, which decides where its slug is looked up.
 *
 * Behind a CDN or a proxy which rewrites the Host header to the origin (e.g. https://domain1.com/slug served
 * from https://lika.example.com/slug), the original host comes from, in order:
 * 1. the header named by LIKA_HOST_HEADER, e.g. X-Lika-Host, set by the CDN. Proxies leave custom headers alone;
 * 2. X-Forwarded-Host, which reverse proxies like Traefik overwrite unless they trust the CDN;
 * 3. Host.
 * When a header lists several hosts, one per proxy, the first one is the client's.
 */
@Component
class RequestHost(private val props: LikaProperties) {

    fun of(request: HttpServletRequest): String? {
        val headers = listOfNotNull(props.hostHeader?.takeIf { it.isNotBlank() }, FORWARDED_HOST, HOST)
        return headers.firstNotNullOfOrNull { normalize(request.getHeader(it)) }
    }

    companion object {
        const val FORWARDED_HOST = "X-Forwarded-Host"
        const val HOST = "Host"

        /** First value of a list, without spaces nor case: hosts are stored lower case */
        fun normalize(value: String?): String? = value?.split(',')?.first()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    }
}
