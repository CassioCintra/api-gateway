# syntax=docker/dockerfile:1
# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — build
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml mvnw ./
COPY .mvn .mvn

RUN --mount=type=cache,target=/root/.m2/repository \
    ./mvnw dependency:go-offline -B -q

COPY src ./src

RUN --mount=type=cache,target=/root/.m2/repository \
    ./mvnw package -DskipTests -B -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — extract layered JAR
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS extractor
WORKDIR /extracted
COPY --from=builder /build/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ─────────────────────────────────────────────────────────────────────────────
# Stage 3 — runtime
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
RUN chown spring:spring /app

COPY --from=extractor --chown=spring:spring /extracted/dependencies ./
COPY --from=extractor --chown=spring:spring /extracted/spring-boot-loader ./
COPY --from=extractor --chown=spring:spring /extracted/snapshot-dependencies ./
COPY --from=extractor --chown=spring:spring /extracted/application ./

USER spring

EXPOSE 8090

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "org.springframework.boot.loader.launch.JarLauncher"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8090/actuator/health || exit 1
