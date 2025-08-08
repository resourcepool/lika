package li.lika.webapp

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import li.lika.common.security.AccountRegistry
import li.lika.core.props.CorsProperties
import li.lika.core.props.ServerProperties
import li.lika.security.*
import li.lika.security.models.security.Roles
import li.lika.security.props.SecurityProperties
import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
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
    private val securityProperties: SecurityProperties,
    private val mapper: ObjectMapper,
    private val registry: AccountRegistry
) {

    private val logger = KotlinLogging.logger {}

    @PostConstruct
    fun logEnvInfo() {
        logger.info("========================== Env Info ==========================")
        logger.info("Server properties: " + serverProperties.port)
        logger.info("Cors properties: " + corsProperties.allowedOrigins)
        logger.info("Security properties: defaultAccessToken: ${securityProperties.defaultGlobalAccessToken}")
        logger.info("=================================================================\n")
    }

    private fun authenticationManager(): AuthenticationManager {
        return AuthManager(registry, securityProperties)
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        val securityFailuresHandler = AppSecurityFailuresHandler(mapper)
        val authFilter = AuthenticationFilter(authenticationManager(), AppAuthenticationConverter())
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
        if (corsProperties.enabled == true) {
            filterConfig
                .addFilter(corsFilter())
        }
        filterConfig
            .addFilterBefore(authFilter, BasicAuthenticationFilter::class.java)
            .authorizeHttpRequests { customizer ->
                customizer
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/api/search/**")
                    .hasAnyAuthority(
                        *(Roles.distinct(
                            Roles.globalRoleAdmin().toAuthority(),
                            Roles.globalRoleRead().toAuthority(),
                            Roles.orgRoleReadAnyOrgAnyDomain().toAuthority(),
                            Roles.orgRoleReadWriteAnyOrgAnyDomain().toAuthority()
                        ))
                    )
                    .requestMatchers("/api/links/**")
                    .hasAnyAuthority(
                        *(Roles.distinct(
                            Roles.globalRoleAdmin().toAuthority(),
                        Roles.orgRoleReadWriteAnyOrgAnyDomain().toAuthority()
                        ))
                    )
                    .requestMatchers("/api/domains/**")
                    .hasAnyAuthority(
                        *(Roles.distinct(Roles.globalRoleAdmin().toAuthority(),
                        Roles.orgRoleReadWriteAnyOrgAnyDomain().toAuthority()))
                    )
                    .requestMatchers("/api/admin/**")
                    .hasAuthority(Roles.globalRoleAdmin().toAuthority())
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
