# API Gateway

Ponto de entrada único do ecossistema Switchboard. Recebe todas as requisições externas, valida o JWT via `ms-auth` e roteia para o microsserviço correto.

**Porta:** `8090`
**Stack:** Spring Boot 4.0.6 · Spring Cloud Gateway Server WebFlux 5.0.1 · Java 25

---

## Responsabilidades

- **Roteamento** — encaminha cada prefixo de path para o serviço correto, reescrevendo o path quando necessário.
- **Validação JWT** — verifica o token Bearer antes de rotear; requisições sem JWT válido recebem `401 Unauthorized`.
- **Rate limiting** — limita chamadas ao endpoint `/flags/**` (SDK) por token de API via Redis.

---

## Tabela de rotas

| Prefixo externo | Serviço de destino | Path no serviço |
|---|---|---|
| `/auth/**` | ms-auth `:9000` | `/auth/**` (pass-through) |
| `/flags/**` | ms-feature-flags `:8081` | `/feature-flag/v1/**` |
| `/workspaces/**` | ms-workspace-management `:8082` | `/users/v1/workspaces/**` |
| `/tokens/**` | ms-workspace-management `:8082` | `/users/v1/tokens/**` |
| `/dashboard/**` | ms-dashboard `:8083` | `/dashboard/v1/**` *(Fase 6)* |
| `/audit/**` | ms-audit `:8084` | `/audit/v1/**` *(Fase 5)* |

> `/auth/**` não exige JWT — o ms-auth gerencia sua própria autenticação (login, registro, OAuth2).

---

## Correlação de requisições

Todas as requisições recebem um header `X-Correlator` com um UUID que acompanha o log em todos os serviços. O `CorrelatorFilter` (`filter/CorrelatorFilter.java`) propaga o header recebido ou gera um novo caso ausente. O valor é exposto no response e registrado nos logs via MDC (`%X{correlator}`).

---

## Documentação da API (Swagger UI)

Disponível em `http://localhost:8090/swagger-ui.html` — sem necessidade de JWT.

O gateway serve specs OpenAPI estáticas de cada microsserviço a partir de `src/main/resources/static/openapi/`. O Swagger UI exibe um seletor para navegar entre os serviços. **Os serviços não precisam estar ativos** para visualizar a documentação.

### Atualizando um spec

Quando a API de um microsserviço mudar, regenere e commite o arquivo correspondente:

```bash
# no repositório do microsserviço
./mvnw verify   # gera target/openapi.yaml (requer springdoc configurado)

# copie para o gateway
cp target/openapi.yaml ../gateway/src/main/resources/static/openapi/ms-<nome>.yaml
```

| Arquivo | Serviço |
|---|---|
| `static/openapi/ms-auth.yaml` | ms-auth `:9000` |
| `static/openapi/ms-feature-flags.yaml` | ms-feature-flags `:8081` |
| `static/openapi/ms-workspace-management.yaml` | ms-workspace-management `:8082` |
| `static/openapi/ms-audit.yaml` | ms-audit `:8084` |
| `static/openapi/ms-dashboard.yaml` | ms-dashboard `:8083` |

---

## Segurança

A validação JWT é feita pelo `spring-security-oauth2-resource-server` apontado para o `ms-auth`:

```yaml
spring.security.oauth2.resourceserver.jwt.issuer-uri: http://ms-auth:9000
```

O `SecurityConfig` define duas regras:

```
/auth/**  →  permitAll   (login, registro, OAuth2 token endpoint)
/**       →  authenticated() com Bearer JWT
```

O `KeyResolver` extrai a chave de rate limiting do header `Authorization`. Se ausente, usa o IP do cliente.

---

## Rate limiting

Aplicado a **todas as rotas** via `RedisRateLimiter` (token bucket). A chave é o valor do header `Authorization`; fallback para IP do cliente, depois `"anonymous"`.

| Rota | `replenishRate` | `burstCapacity` | Variáveis de ambiente |
|---|---|---|---|
| `/auth/**` | 10 req/s | 20 req/s | `RATE_AUTH_REPLENISH` / `RATE_AUTH_BURST` |
| `/flags/**` | 20 req/s | 40 req/s | `RATE_FLAGS_REPLENISH` / `RATE_FLAGS_BURST` |
| `/workspaces/**` | 30 req/s | 60 req/s | `RATE_WORKSPACES_REPLENISH` / `RATE_WORKSPACES_BURST` |
| `/tokens/**` | 10 req/s | 20 req/s | `RATE_TOKENS_REPLENISH` / `RATE_TOKENS_BURST` |
| `/dashboard/**` | 30 req/s | 60 req/s | `RATE_DASHBOARD_REPLENISH` / `RATE_DASHBOARD_BURST` |
| `/audit/**` | 20 req/s | 40 req/s | `RATE_AUDIT_REPLENISH` / `RATE_AUDIT_BURST` |

Requisições acima do burst recebem `429 Too Many Requests`.

---

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `MS_AUTH_ISSUER_URI` | `http://ms-auth:9000` | Issuer URI para validação JWT |
| `MS_AUTH_URI` | `http://ms-auth:9000` | URI de roteamento do ms-auth |
| `MS_FEATURE_FLAGS_URI` | `http://ms-feature-flags:8081` | URI do ms-feature-flags |
| `MS_WORKSPACE_URI` | `http://ms-workspace-management:8082` | URI do ms-workspace-management |
| `MS_DASHBOARD_URI` | `http://ms-dashboard:8083` | URI do ms-dashboard |
| `MS_AUDIT_URI` | `http://ms-audit:8084` | URI do ms-audit |
| `REDIS_HOST` | `localhost` | Host do Redis |
| `REDIS_PORT` | `6379` | Porta do Redis |
| `AUTH_PUBLIC_ISSUER_URI` | `http://localhost:9000` | Issuer URI público (usado pelo Swagger UI para OAuth2) |
| `GATEWAY_PUBLIC_URL` | `http://localhost:8090` | URL pública do gateway (OAuth2 redirect do Swagger UI) |
| `RATE_AUTH_REPLENISH` | `10` | Rate limit replenish de `/auth/**` (req/s) |
| `RATE_AUTH_BURST` | `20` | Rate limit burst de `/auth/**` (req/s) |
| `RATE_FLAGS_REPLENISH` | `20` | Rate limit replenish de `/flags/**` (req/s) |
| `RATE_FLAGS_BURST` | `40` | Rate limit burst de `/flags/**` (req/s) |
| `RATE_WORKSPACES_REPLENISH` | `30` | Rate limit replenish de `/workspaces/**` (req/s) |
| `RATE_WORKSPACES_BURST` | `60` | Rate limit burst de `/workspaces/**` (req/s) |
| `RATE_TOKENS_REPLENISH` | `10` | Rate limit replenish de `/tokens/**` (req/s) |
| `RATE_TOKENS_BURST` | `20` | Rate limit burst de `/tokens/**` (req/s) |
| `RATE_DASHBOARD_REPLENISH` | `30` | Rate limit replenish de `/dashboard/**` (req/s) |
| `RATE_DASHBOARD_BURST` | `60` | Rate limit burst de `/dashboard/**` (req/s) |
| `RATE_AUDIT_REPLENISH` | `20` | Rate limit replenish de `/audit/**` (req/s) |
| `RATE_AUDIT_BURST` | `40` | Rate limit burst de `/audit/**` (req/s) |

---

## Executando localmente

**Pré-requisitos:** `ms-auth` em execução (porta 9000) e Redis disponível (porta 6379).

```bash
# Subir Redis com Docker
docker run -d -p 6379:6379 redis:7-alpine

# Executar o gateway
./mvnw spring-boot:run
```

Para apontar para serviços locais em portas não-padrão, sobrescreva via variáveis de ambiente:

```bash
MS_AUTH_URI=http://localhost:9000 \
MS_FEATURE_FLAGS_URI=http://localhost:8081 \
MS_WORKSPACE_URI=http://localhost:8082 \
./mvnw spring-boot:run
```

---

## Testes

```bash
./mvnw verify
```

Os testes requerem Docker para o Testcontainers (Redis). O `ReactiveJwtDecoder` é substituído por um mock, dispensando o ms-auth em execução durante os testes.

| Teste | O que verifica |
|---|---|
| `GatewayApplicationTests` | Contexto Spring carrega sem erros |
| `SecurityConfigTest` | `/flags/**` sem JWT → `401`; `/auth/**` sem JWT → não `401` |
| `RateLimiterTest` | Rate limiting aplicado por rota conforme configuração |
| `CorrelatorFilterTest` | `X-Correlator` propagado ou gerado; exposto no response |

Os endpoints do Swagger UI (`/swagger-ui/**`, `/swagger-ui.html`) e os specs estáticos (`/openapi/**`) são públicos — não exigem JWT — conforme configurado no `SecurityConfig`.

---

## Nota sobre dependências

O `spring-cloud-starter-gateway` `4.x` (BOM `2025.0.2`) é **incompatível** com Spring Boot 4.x devido à reorganização de pacotes (`org.springframework.boot.web.context` → `org.springframework.boot.web.server.context` e Jackson 2.x → 3.x). Este projeto usa o novo artifactId da versão 5.x:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
    <version>5.0.1</version>
</dependency>
```
