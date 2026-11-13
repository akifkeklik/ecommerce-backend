package com.ecommerce.infrastructure.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    @Test
    void clientRegistryRemainsBoundedWhenNewAddressesArrive() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "capacity", 10);
        ReflectionTestUtils.setField(filter, "refillTokens", 10);
        ReflectionTestUtils.setField(filter, "refillDuration", 60);
        ReflectionTestUtils.setField(filter, "maxClients", 1);
        ReflectionTestUtils.setField(filter, "idleTimeoutSeconds", 900L);

        MockHttpServletRequest firstRequest = requestFrom("192.0.2.10");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> { };

        filter.doFilter(firstRequest, firstResponse, chain);

        assertThat(firstResponse.getStatus()).isEqualTo(200);

        MockHttpServletRequest secondRequest = requestFrom("192.0.2.11");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();

        filter.doFilter(secondRequest, secondResponse, chain);

        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("1");

        Map<?, ?> buckets = (Map<?, ?>) ReflectionTestUtils.getField(filter, "buckets");
        assertThat(buckets).hasSize(1);
    }

    private MockHttpServletRequest requestFrom(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
