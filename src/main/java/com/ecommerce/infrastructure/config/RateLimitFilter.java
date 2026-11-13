package com.ecommerce.infrastructure.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
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
 * In-memory, per-remote-address rate limiting for a single application instance.
 *
 * <p>The registry is bounded to avoid untrusted clients exhausting heap by
 * creating an unbounded number of distinct remote-address entries. For
 * multi-instance deployments, use a shared Bucket4j backend (for example Redis).</p>
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;

    @Value("${rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${rate-limit.capacity:100}")
    private int capacity;

    @Value("${rate-limit.refill-tokens:100}")
    private int refillTokens;

    @Value("${rate-limit.refill-duration:60}")
    private int refillDuration;

    @Value("${rate-limit.max-clients:10000}")
    private int maxClients;

    @Value("${rate-limit.idle-timeout-seconds:900}")
    private long idleTimeoutSeconds;

    private final Map<String, BucketEntry> buckets = new ConcurrentHashMap<>();
    private final Object registryLock = new Object();
    private volatile long lastCleanupAt;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        BucketEntry entry = getOrCreateBucket(httpRequest.getRemoteAddr());

        if (entry == null || !entry.bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded or client registry is full");
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setHeader("Retry-After", "1");
            httpResponse.setContentType("application/json");
            httpResponse.setCharacterEncoding("UTF-8");
            httpResponse.getWriter().write(
                    "{\"error\":\"Too many requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}");
            return;
        }

        httpResponse.setHeader("X-Rate-Limit-Remaining",
                String.valueOf(entry.bucket.getAvailableTokens()));
        chain.doFilter(request, response);
    }

    private BucketEntry getOrCreateBucket(String clientId) {
        long now = System.currentTimeMillis();
        BucketEntry existing = buckets.get(clientId);
        if (existing != null) {
            existing.lastAccessAt = now;
            return existing;
        }

        synchronized (registryLock) {
            existing = buckets.get(clientId);
            if (existing != null) {
                existing.lastAccessAt = now;
                return existing;
            }

            if (now - lastCleanupAt >= CLEANUP_INTERVAL_MILLIS) {
                long idleThreshold = now - Duration.ofSeconds(Math.max(1L, idleTimeoutSeconds)).toMillis();
                buckets.entrySet().removeIf(entry -> entry.getValue().lastAccessAt < idleThreshold);
                lastCleanupAt = now;
            }

            if (buckets.size() >= Math.max(1, maxClients)) {
                return null;
            }

            BucketEntry created = new BucketEntry(createBucket(), now);
            buckets.put(clientId, created);
            return created;
        }
    }

    private Bucket createBucket() {
        if (capacity < 1 || refillTokens < 1 || refillDuration < 1) {
            throw new IllegalStateException("Rate-limit capacity, refill tokens and duration must be positive");
        }

        Bandwidth limit = Bandwidth.classic(
                capacity,
                Refill.greedy(refillTokens, Duration.ofSeconds(refillDuration)));
        return Bucket.builder().addLimit(limit).build();
    }

    private static final class BucketEntry {
        private final Bucket bucket;
        private volatile long lastAccessAt;

        private BucketEntry(Bucket bucket, long lastAccessAt) {
            this.bucket = bucket;
            this.lastAccessAt = lastAccessAt;
        }
    }
}
