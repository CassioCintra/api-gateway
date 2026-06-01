package io.github.cassio.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class CorrelatorFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlator";
    public static final String MDC_KEY = "correlator";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String correlator = request.getHeaders().getFirst(HEADER);
        boolean propagated = correlator != null && !correlator.isBlank();
        if (!propagated) {
            correlator = UUID.randomUUID().toString();
        }

        final String finalCorrelator = correlator;
        final String uri = buildUri(request);
        final long start = System.currentTimeMillis();

        exchange.getResponse().getHeaders().set(HEADER, finalCorrelator);

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(r -> r.header(HEADER, finalCorrelator))
                .build();

        try {
            MDC.put(MDC_KEY, finalCorrelator);
            log.info("→ {} {}", request.getMethod(), uri);
        } finally {
            MDC.remove(MDC_KEY);
        }

        return chain.filter(mutatedExchange)
                .doFinally(signalType -> {
                    try {
                        MDC.put(MDC_KEY, finalCorrelator);
                        long duration = System.currentTimeMillis() - start;
                        int status = exchange.getResponse().getStatusCode() != null
                                ? exchange.getResponse().getStatusCode().value() : 0;
                        log.info("← {} {} [Status:{} | {}ms]", request.getMethod(), uri, status, duration);
                    } finally {
                        MDC.remove(MDC_KEY);
                    }
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private String buildUri(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        String query = request.getURI().getRawQuery();
        return query != null ? path + "?" + query : path;
    }
}
