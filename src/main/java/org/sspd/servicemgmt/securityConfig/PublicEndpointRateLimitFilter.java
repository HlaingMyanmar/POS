package org.sspd.servicemgmt.securityConfig;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class PublicEndpointRateLimitFilter extends OncePerRequestFilter {
    private static final Map<String, Policy> POLICIES = Map.ofEntries(
            Map.entry("/api/v1/auth/login", new Policy("staff-login", 10, Duration.ofMinutes(1))),
            Map.entry("/api/v1/auth/refresh", new Policy("staff-refresh", 30, Duration.ofMinutes(1))),
            Map.entry("/api/v1/customer-portal/auth/login", new Policy("customer-login", 10, Duration.ofMinutes(1))),
            Map.entry("/api/v1/customer-portal/auth/google", new Policy("customer-google", 20, Duration.ofMinutes(1))),
            Map.entry("/api/v1/customer-portal/auth/register", new Policy("customer-register", 5, Duration.ofMinutes(10))),
            Map.entry("/api/v1/customer-portal/auth/forgot", new Policy("customer-forgot", 3, Duration.ofMinutes(15))),
            Map.entry("/api/v1/customer-portal/auth/reset", new Policy("customer-reset", 5, Duration.ofMinutes(15))),
            Map.entry("/api/v1/setup/initial-admin", new Policy("initial-admin", 5, Duration.ofMinutes(10))),
            Map.entry("/api/v1/scan", new Policy("barcode-scan", 120, Duration.ofMinutes(1)))
    );

    private final Cache<String, TokenBucket> buckets = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(2, TimeUnit.HOURS)
            .build();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Policy policy = POLICIES.get(request.getRequestURI());
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientAddress = request.getRemoteAddr();
        String key = policy.name() + ":" + (clientAddress == null ? "unknown" : clientAddress);
        TokenBucket bucket = buckets.get(key, ignored -> new TokenBucket(policy));
        Decision decision = bucket.tryConsume();

        response.setHeader("X-RateLimit-Limit", Integer.toString(policy.capacity()));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(decision.remaining()));
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"Too many requests. Please try again later.\",\"data\":null}"
        );
    }

    private record Policy(String name, int capacity, Duration refillPeriod) {
    }

    private record Decision(boolean allowed, int remaining, long retryAfterSeconds) {
    }

    private static final class TokenBucket {
        private final Policy policy;
        private double tokens;
        private long lastRefillNanos;

        private TokenBucket(Policy policy) {
            this.policy = policy;
            this.tokens = policy.capacity();
            this.lastRefillNanos = System.nanoTime();
        }

        private synchronized Decision tryConsume() {
            long now = System.nanoTime();
            long elapsed = Math.max(0, now - lastRefillNanos);
            double tokensPerNano = (double) policy.capacity() / policy.refillPeriod().toNanos();
            tokens = Math.min(policy.capacity(), tokens + elapsed * tokensPerNano);
            lastRefillNanos = now;

            if (tokens >= 1.0d) {
                tokens -= 1.0d;
                return new Decision(true, (int) Math.floor(tokens), 0);
            }
            long retryNanos = (long) Math.ceil((1.0d - tokens) / tokensPerNano);
            long retrySeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(retryNanos) + 1);
            return new Decision(false, 0, retrySeconds);
        }
    }
}
