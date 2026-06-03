package io.github.cassio.gateway.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.gateway.server.webflux.httpclient.connect-timeout=100",
                "spring.cloud.gateway.server.webflux.httpclient.response-timeout=500ms",
                "RATE_AUTH_REPLENISH=1",      "RATE_AUTH_BURST=5",
                "RATE_FLAGS_REPLENISH=1",     "RATE_FLAGS_BURST=5",
                "RATE_WORKSPACES_REPLENISH=1","RATE_WORKSPACES_BURST=5",
                "RATE_TOKENS_REPLENISH=1",    "RATE_TOKENS_BURST=5",
                "RATE_DASHBOARD_REPLENISH=1", "RATE_DASHBOARD_BURST=5",
                "RATE_AUDIT_REPLENISH=1",     "RATE_AUDIT_BURST=5"
        }
)
class RateLimiterTest {

    private static final String BEARER_TOKEN = "Bearer test-token";
    private static final Jwt JWT = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .claim("sub", "test-user")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static final GenericContainer<?> WIREMOCK = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.13.2-3"))
            .withExposedPorts(8080);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        String url = wireMockUrl();
        registry.add("MS_AUTH_URI",          () -> url);
        registry.add("MS_FEATURE_FLAGS_URI", () -> url);
        registry.add("MS_WORKSPACE_URI",     () -> url);
        registry.add("MS_DASHBOARD_URI",     () -> url);
        registry.add("MS_AUDIT_URI",         () -> url);
    }

    @BeforeAll
    static void configureStubs() throws Exception {
        HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create(wireMockUrl() + "/__admin/mappings"))
                        .POST(HttpRequest.BodyPublishers.ofString(
                                """
                                {"request":{"method":"ANY","urlPattern":".*"},\
                                "response":{"status":200}}"""))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.discarding()
        );
    }

    private static String wireMockUrl() {
        return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
    }

    @LocalServerPort
    int port;

    @MockitoBean
    ReactiveJwtDecoder jwtDecoder;

    WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.just(JWT));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/auth/login",
            "/flags/evaluate",
            "/workspaces/test",
            "/tokens/test",
            "/dashboard/test",
            "/audit/test"
    })
    void shouldReturn429WhenRateLimitExceeded(String uri) {
        // Exhaust the burst bucket (5 tokens at 1 token/s replenish gives ~5s CI tolerance)
        for (int i = 0; i < 5; i++) {
            webTestClient.get().uri(uri)
                    .header("Authorization", BEARER_TOKEN)
                    .exchange()
                    .expectStatus().isOk();
        }

        webTestClient.get().uri(uri)
                .header("Authorization", BEARER_TOKEN)
                .exchange()
                .expectStatus().isEqualTo(429);
    }
}
