# syntax=docker/dockerfile:1

# MatchCV.ai backend container image.
#
# Multi-stage on purpose: Maven, the JDK compiler and the ~500 MB local
# repository stay in the build stage and never reach the shipped image, which
# carries a JRE and one jar. Both stages are pinned to Java 25 — the project
# targets Java 25 (pom.xml <java.version>) and Spring Boot 4.1.1 requires
# Java 17+, so nothing here needs to be downgraded.

# ---------------------------------------------------------------------------
# Stage 1 — build
# ---------------------------------------------------------------------------
FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /build

# Dependencies resolve from pom.xml alone, so this layer stays cached until
# the pom itself changes. A source-only edit then rebuilds without
# re-downloading the dependency tree.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src

# Tests deliberately do not run here. They are a full Spring context on
# in-memory H2 (83 of them) and belong in `./mvnw test` locally and in CI, not
# in a deploy build where a flake would block a release. Skipping does not hide
# compilation errors: `package` still compiles both main and test sources.
RUN mvn -B -ntp -DskipTests package

# spring-boot:repackage leaves exactly one *.jar (the executable one) next to
# *.jar.original. Assert the count rather than globbing blindly, so a future
# module, classifier or shade step fails the build instead of quietly shipping
# the wrong artifact.
RUN set -eu; \
    count="$(ls -1 target/*.jar | wc -l)"; \
    if [ "$count" -ne 1 ]; then \
      echo "Expected exactly one jar in target/, found $count:" >&2; \
      ls -1 target/*.jar >&2; \
      exit 1; \
    fi; \
    cp "$(ls -1 target/*.jar)" /build/app.jar

# ---------------------------------------------------------------------------
# Stage 2 — runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25.0.3_9-jre AS runtime

# Non-root. --create-home matters beyond convention: PDFBox 3 caches its font
# metrics under $HOME, and an unwritable home turns every PDF export into a
# warning-logged cache miss.
RUN useradd --system --create-home --uid 10001 --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar /app/app.jar
USER app

# Documentation only. The listener is not fixed here: application.yml reads
# `${PORT:${SERVER_PORT:8091}}`, so Render's injected PORT wins in deployment
# and 8091 is the local fallback.
EXPOSE 8091

# MaxRAMPercentage, not -Xmx: the heap then follows whatever the container is
# given instead of being guessed at build time. Overridable from the platform's
# environment settings.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# exec so the JVM is PID 1 and receives Render's SIGTERM directly, which is
# what lets Spring shut the Hikari pool down cleanly on a redeploy.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
