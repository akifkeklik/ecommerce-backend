# Build the executable JAR with a pinned Java/Maven toolchain.
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# Keep the runtime image small and run as a non-root user.
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -g 1001 -S appgroup \
    && adduser -u 1001 -S appuser -G appgroup \
    && mkdir -p /app/logs /app/uploads \
    && chown -R appuser:appgroup /app

COPY --from=builder --chown=appuser:appgroup /app/target/*.jar /app/app.jar

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
