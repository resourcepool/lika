package li.lika.webapp

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(buildProps: ObjectProvider<BuildProperties>): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Lika API")
                    .description("Open-source permalink API")
                    .version(buildProps.ifAvailable?.version ?: "unknown")
                    .license(License().name("AGPL-3.0").url("https://www.gnu.org/licenses/agpl-3.0.html"))
            )
            .components(
                Components().addSecuritySchemes(
                    BEARER_AUTH,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("Access token, sent as `Authorization: Bearer <token>`")
                )
            )
            .addSecurityItem(SecurityRequirement().addList(BEARER_AUTH))
    }

    companion object {
        const val BEARER_AUTH = "bearerAuth"
    }
}
