package li.lika.security

import org.springframework.http.HttpMethod
import org.springframework.security.web.util.matcher.RequestMatcher
import jakarta.servlet.http.HttpServletRequest

class AppRequestMatcher : RequestMatcher {
    override fun matches(request: HttpServletRequest): Boolean {
        if (HttpMethod.OPTIONS.matches(request.method)) {
            return false
        }
        // NB: ALL uris will be processed by this filter. NOTHING is public here.
        // All non-authenticated requests will be considered "anonymous" with authority "role_anonymous"
        return true
    }

}
