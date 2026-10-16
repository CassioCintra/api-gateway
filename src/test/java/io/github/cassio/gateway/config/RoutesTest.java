package io.github.cassio.gateway.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
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
                "spring.cloud.gateway.server.webflux.httpclient.response-timeout=500ms"
        }
)
class RoutesTest {

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
        var url = wireMockUrl();
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
                                "response":{"status":200,"body":"{{request.path}}","transformers":["response-template"]}}"""))
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
    @CsvSource({
            "POST, /workspaces,                 /users/v1/workspaces",
            "GET,  /workspaces/abc/members,     /users/v1/workspaces/abc/members",
            "GET,  /flags/flags,                /feature-flag/v1/flags",
            "GET,  /audit/events,               /audit/v1/events"
    })
    void shouldRewritePathToTheServiceContextPath(String method, String uri, String expectedPath) {
        webTestClient.method(HttpMethod.valueOf(method)).uri(uri)
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(expectedPath);
    }
}
