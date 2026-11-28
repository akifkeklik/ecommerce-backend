package com.ecommerce;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
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
@ContextConfiguration(initializers = RenderBlueprintStartupTest.RenderEnvironment.class)
class RenderBlueprintStartupTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * Render passes these as operating-system environment variables, so they are added as a
     * SystemEnvironmentPropertySource: Spring's relaxed binding then treats them exactly as on Render
     * (MANAGEMENT_HEALTH_REDIS_ENABLED -> management.health.redis.enabled, and a misspelt key stays unbound).
     */
    static class RenderEnvironment implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            Map<String, Object> env = new HashMap<>();
            try {
                for (Map<String, Object> entry : blueprintEnv()) {
                    if (entry.containsKey("value")) {
                        env.put(String.valueOf(entry.get("key")), String.valueOf(entry.get("value")));
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            // What Render fills in from the database and generateValue.
            env.put("DB_HOST", POSTGRES.getHost());
            env.put("DB_PORT", String.valueOf(POSTGRES.getMappedPort(5432)));
            env.put("DB_NAME", POSTGRES.getDatabaseName());
            env.put("DB_USERNAME", POSTGRES.getUsername());
            env.put("DB_PASSWORD", POSTGRES.getPassword());
            env.put("JWT_SECRET", Base64.getEncoder().encodeToString("render-blueprint-test-signing-key".getBytes()));
            context.getEnvironment().getPropertySources()
                    .addFirst(new SystemEnvironmentPropertySource("render-blueprint", env));
        }
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
