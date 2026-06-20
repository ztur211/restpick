package com.ztur211.restpick;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * Rate-limits the Google-spending endpoints: each client IP gets a per-minute
 * budget, and a single global bucket caps total daily calls so the Google bill
 * stays bounded even under distributed load. Other routes pass through.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/autocomplete", "/pick", "/resolve-location", "/map-image", "/photo");

    private final int perIpPerMinute;
    private final Bucket globalBucket;
    private final Cache<String, Bucket> perIpBuckets;

    /** Production limits: 60 requests/min per IP, 5,000 Google calls/day total. */
    public RateLimitFilter() {
        this(60, 5_000);
    }

    RateLimitFilter(int perIpPerMinute, long globalPerDay) {
        this.perIpPerMinute = perIpPerMinute;
        this.globalBucket = Bucket.builder()
                .addLimit(limit -> limit.capacity(globalPerDay).refillGreedy(globalPerDay, Duration.ofDays(1)))
                .build();
        this.perIpBuckets = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(Duration.ofHours(1))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!LIMITED_PATHS.contains(request.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }

        Bucket ipBucket = perIpBuckets.get(clientIp(request), key -> Bucket.builder()
                .addLimit(limit -> limit.capacity(perIpPerMinute).refillGreedy(perIpPerMinute, Duration.ofMinutes(1)))
                .build());

        if (ipBucket.tryConsume(1) && globalBucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Please slow down.\"}");
        }
    }

    /**
     * Client IP, in priority order:
     * 1. {@code CF-Connecting-IP} — Cloudflare (which fronts Render) sets this to the
     *    real client IP; the per-request proxy hops in X-Forwarded-For/remoteAddr rotate.
     * 2. first hop of {@code X-Forwarded-For} — for non-Cloudflare proxies.
     * 3. the socket address — for direct connections / local dev.
     */
    private String clientIp(HttpServletRequest request) {
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
