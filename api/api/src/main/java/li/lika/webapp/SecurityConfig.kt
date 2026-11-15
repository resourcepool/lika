package li.lika.webapp

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import li.lika.common.security.CredentialsResolver
import li.lika.core.props.CorsProperties
import li.lika.core.props.ServerProperties
import li.lika.security.*
import li.lika.security.models.Authorities
import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationFilter
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val serverProperties: ServerProperties,
    private val corsProperties: CorsProperties,
    private val mapper: ObjectMapper
) {

    private val logger = KotlinLogging.logger {}

    @PostConstruct
    fun logEnvInfo() {
        logger.info("========================== Env Info ==========================")
        logger.info("Server properties: " + serverProperties.port)
        logger.info("Cors properties: " + corsProperties.allowedOrigins)
        logger.info("=================================================================\n")
    }

    /**
     * Every request goes through the authentication filter: a user token or an API key makes it authenticated,
     * no credentials leave it anonymous. Finer rights (role per domain) are checked by the services.
     */
    @Bean
    fun filterChain(http: HttpSecurity, resolver: CredentialsResolver): SecurityFilterChain {
        val securityFailuresHandler = AppSecurityFailuresHandler(mapper)
        val authFilter = AuthenticationFilter(AuthManager(resolver), AppAuthenticationConverter())
        authFilter.requestMatcher = AppRequestMatcher()
        authFilter.successHandler = AppAuthenticationSuccessHandler()
        authFilter.failureHandler = securityFailuresHandler
        val filterConfig = http
            .csrf {
                it.disable()
            }
            .httpBasic {
                it.disable()
            }
            .formLogin {
                it.disable()
            }
            .exceptionHandling {
                it.accessDeniedHandler(securityFailuresHandler)
            }
        if (corsProperties.enabled == true) {
            filterConfig
                .addFilter(corsFilter())
        }
        filterConfig
            .addFilterBefore(authFilter, BasicAuthenticationFilter::class.java)
            .authorizeHttpRequests { customizer ->
                customizer
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    // Answers 401 itself, as MCP clients expect
                    .requestMatchers("/mcp", "/mcp/**").permitAll()
                    .requestMatchers("/api/admin/**").hasAuthority(Authorities.ADMIN)
                    .requestMatchers("/api/me/**", "/api/me").hasAuthority(Authorities.USER)
                    // Applications do not manage users or keys, unless they hold an admin key
                    .requestMatchers("/api/users/**", "/api/users", "/api/keys/**", "/api/keys").hasAnyAuthority(Authorities.USER, Authorities.ADMIN)
                    .requestMatchers("/api/**").hasAuthority(Authorities.AUTHENTICATED)
                    .requestMatchers("/**").permitAll()
            }
        return http.build()
    }

    open fun corsFilter(): CorsFilter {
        val config = CorsConfiguration()
        // Possibly...
        config.applyPermitDefaultValues()
        config.allowedOrigins = corsProperties.allowedOrigins
        config.addAllowedMethod(HttpMethod.GET)
        config.addAllowedMethod(HttpMethod.POST)
        config.addAllowedMethod(HttpMethod.PUT)
        config.addAllowedMethod(HttpMethod.PATCH)
        config.addAllowedMethod(HttpMethod.DELETE)
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return CorsFilter(source)
    }
}
