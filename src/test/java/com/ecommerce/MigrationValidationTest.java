package com.ecommerce;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production runs Flyway and then {@code ddl-auto: validate}. The other tests let Hibernate build an H2 schema,
 * so only this test proves the migrations apply on PostgreSQL and match the entities.
 * Runs wherever Docker is available (CI); skipped otherwise.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MigrationValidationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("Every migration applies on PostgreSQL and Hibernate validates the schema against the entities")
    void migrationsApplyAndMatchEntities() {
        // Reaching this line means the context started, i.e. ddl-auto=validate accepted the migrated schema.
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).hasSizeGreaterThanOrEqualTo(9);
    }
}
