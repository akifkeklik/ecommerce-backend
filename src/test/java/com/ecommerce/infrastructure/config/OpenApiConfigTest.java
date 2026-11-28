package com.ecommerce.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenAPI openApi = new OpenApiConfig().customOpenAPI();

    @Test
    void tryItOutTargetsTheServingHost() {
        assertThat(openApi.getServers()).extracting(Server::getUrl).containsExactly("/");
    }

    @Test
    void linksPointToTheRepositoryNotPlaceholderDomains() {
        assertThat(openApi.getInfo().getTermsOfService()).isNull();
        assertThat(openApi.getInfo().getContact().getEmail()).isNull();
        assertThat(openApi.getInfo().getContact().getUrl())
                .isEqualTo("https://github.com/akifkeklik/ecommerce-backend");
    }
}
