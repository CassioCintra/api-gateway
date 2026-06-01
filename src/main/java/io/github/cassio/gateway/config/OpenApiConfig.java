package io.github.cassio.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Value("${auth.public-issuer-uri:http://localhost:9000}")
    private String publicIssuerUri;

    @Bean
    public OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("oauth2",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.OAUTH2)
                                        .flows(new OAuthFlows()
                                                .authorizationCode(new OAuthFlow()
                                                        .authorizationUrl(publicIssuerUri + "/oauth2/authorize")
                                                        .tokenUrl(publicIssuerUri + "/oauth2/token")
                                                        .scopes(new Scopes()
                                                                .addString("openid",  "Identidade")
                                                                .addString("profile", "Perfil")
                                                                .addString("email",   "E-mail"))))));
    }
}
