package io.github.cassio.gateway.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.gateway.server.webflux.httpclient.connect-timeout=100",
                "spring.cloud.gateway.server.webflux.httpclient.response-timeout=500ms"
        }
)
class SecurityConfigTest {

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
        String wireMockUrl = wireMockUrl();
        registry.add("MS_AUTH_URI", () -> wireMockUrl);
        registry.add("MS_FEATURE_FLAGS_URI", () -> wireMockUrl);
        registry.add("MS_WORKSPACE_URI", () -> wireMockUrl);
        registry.add("MS_DASHBOARD_URI", () -> wireMockUrl);
        registry.add("MS_AUDIT_URI", () -> wireMockUrl);
    }

    @BeforeAll
    static void configureStubs() throws Exception {
        HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create(wireMockUrl() + "/__admin/mappings"))
                        .POST(HttpRequest.BodyPublishers.ofString(
                                """
                                {"request":{"method":"ANY","urlPattern":".*"},\
                                "response":{"status":200}}"""
                        ))
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
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/flags/",
            "/workspaces/",
            "/tokens/",
            "/dashboard/",
            "/audit/"
    })
    void shouldRejectUnauthenticatedRequestsOnProtectedRoutes(String uri) {
        webTestClient.get()
                .uri(uri)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldPermitAuthEndpointsWithoutToken() {
        webTestClient.get()
                .uri("/auth/any")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldPermitSwaggerUiWithoutToken() {
        webTestClient.get()
                .uri("/swagger-ui.html")
                .exchange()
                .expectStatus().is3xxRedirection();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/openapi/ms-auth.yaml",
            "/openapi/ms-feature-flags.yaml",
            "/openapi/ms-workspace-management.yaml",
            "/openapi/ms-audit.yaml",
            "/openapi/ms-dashboard.yaml"
    })
    void shouldRejectOpenApiSpecsWithoutToken(String uri) {
        webTestClient.get()
                .uri(uri)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
