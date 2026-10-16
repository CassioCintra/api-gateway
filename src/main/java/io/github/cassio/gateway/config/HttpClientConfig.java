package io.github.cassio.gateway.config;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class HttpClientConfig {

    static final Duration DNS_CACHE_MAX_TTL = Duration.ofSeconds(10);

    @Bean
    HttpClientCustomizer shortDnsCacheCustomizer() {
        return httpClient -> httpClient.resolver(spec -> spec.cacheMaxTimeToLive(DNS_CACHE_MAX_TTL));
    }
}
