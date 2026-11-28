package com.ecommerce;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the app the way render.yaml deploys it: the prod profile, the blueprint's environment values,
 * and a real PostgreSQL in place of Render's database. If the blueprint is wrong, this fails in CI
 * instead of on Render. Runs wherever Docker is available; skipped otherwise.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class RenderBlueprintStartupTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void blueprint(DynamicPropertyRegistry registry) throws IOException {
        for (Map<String, Object> env : blueprintEnv()) {
            if (env.containsKey("value")) {
                String value = String.valueOf(env.get("value"));
                registry.add(String.valueOf(env.get("key")), () -> value);
            }
        }
        // What Render fills in from the database and generateValue.
        registry.add("DB_HOST", POSTGRES::getHost);
        registry.add("DB_PORT", () -> POSTGRES.getMappedPort(5432));
        registry.add("DB_NAME", POSTGRES::getDatabaseName);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("JWT_SECRET", () -> Base64.getEncoder().encodeToString(new byte[32]).replace('A', 'k'));
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> blueprintEnv() throws IOException {
        try (InputStream in = Files.newInputStream(Path.of("render.yaml"))) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> service = ((List<Map<String, Object>>) root.get("services")).get(0);
            return (List<Map<String, Object>>) service.get("envVars");
        }
    }

    @Autowired
    private TestRestTemplate http;

    @Test
    @DisplayName("render.yaml starts the prod profile on PostgreSQL: health is UP and Swagger UI is served")
    void blueprintStartsHealthy() {
        ResponseEntity<String> health = http.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");

        assertThat(http.getForEntity("/api-docs", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/swagger-ui/index.html", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
