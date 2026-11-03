package com.ecommerce.infrastructure.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client rate limiting. The remote address is used deliberately:
 * X-Forwarded-For and X-Real-IP are client-controlled unless a trusted-proxy
 * chain is explicitly configured.
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    @Value("${rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${rate-limit.capacity:100}")
    private int capacity;

    @Value("${rate-limit.refill-tokens:100}")
    private int refillTokens;

    @Value("${rate-limit.refill-duration:60}")
    private int refillDuration;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientId = httpRequest.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(clientId, this::createBucket);

        if (bucket.tryConsume(1)) {
            httpResponse.setHeader("X-Rate-Limit-Remaining",
                    String.valueOf(bucket.getAvailableTokens()));
            chain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit exceeded for remote address: {}", clientId);
        httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        httpResponse.setContentType("application/json");
        httpResponse.setCharacterEncoding("UTF-8");
        httpResponse.getWriter().write(
                "{\"error\":\"Too many requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}");
    }

    private Bucket createBucket(String clientId) {
        Bandwidth limit = Bandwidth.classic(
                capacity,
                Refill.greedy(refillTokens, Duration.ofSeconds(refillDuration)));
        return Bucket.builder().addLimit(limit).build();
    }
}
