package io.github.cassio.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelatorFilterTest {

    private final CorrelatorFilter filter = new CorrelatorFilter();

    @Test
    void shouldHaveHighestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void shouldGenerateCorrelatorWhenAbsentFromRequest() {
        MockServerWebExchange exchange = exchangeFor("/flags/test");
        GatewayFilterChain chain = ex -> Mono.empty();

        filter.filter(exchange, chain).block();

        String correlator = exchange.getResponse().getHeaders().getFirst(CorrelatorFilter.HEADER);
        assertThat(correlator).isNotBlank();
    }

    @Test
    void shouldPropagateCorrelatorWhenPresentInRequest() {
        String incoming = "existing-correlator-id";
        MockServerWebExchange exchange = exchangeFor("/flags/test", incoming);
        GatewayFilterChain chain = ex -> Mono.empty();

        filter.filter(exchange, chain).block();

        String correlator = exchange.getResponse().getHeaders().getFirst(CorrelatorFilter.HEADER);
        assertThat(correlator).isEqualTo(incoming);
    }

    @Test
    void shouldForwardCorrelatorToDownstreamRequest() {
        String incoming = "upstream-id";
        MockServerWebExchange exchange = exchangeFor("/flags/test", incoming);

        ServerWebExchange[] captured = new ServerWebExchange[1];
        GatewayFilterChain chain = ex -> {
            captured[0] = ex;
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(captured[0].getRequest().getHeaders().getFirst(CorrelatorFilter.HEADER))
                .isEqualTo(incoming);
    }

    @Test
    void shouldGenerateAndForwardCorrelatorToDownstreamWhenAbsent() {
        MockServerWebExchange exchange = exchangeFor("/flags/test");

        ServerWebExchange[] captured = new ServerWebExchange[1];
        GatewayFilterChain chain = ex -> {
            captured[0] = ex;
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        String responseCorrelator = exchange.getResponse().getHeaders().getFirst(CorrelatorFilter.HEADER);
        String downstreamCorrelator = captured[0].getRequest().getHeaders().getFirst(CorrelatorFilter.HEADER);
        assertThat(downstreamCorrelator).isNotBlank().isEqualTo(responseCorrelator);
    }

    private static MockServerWebExchange exchangeFor(String uri) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(uri).build());
    }

    private static MockServerWebExchange exchangeFor(String uri, String correlator) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(uri).header(CorrelatorFilter.HEADER, correlator).build()
        );
    }
}
