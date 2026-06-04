package com.financeiro_api.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting por IP nos endpoints de autenticação sensíveis.
 * - /auth/login:              10 requisições por minuto
 * - /auth/esqueci-senha:       5 requisições por minuto
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> loginBuckets      = new ConcurrentHashMap<>();
    private final Map<String, Bucket> esqueciSenhaBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip   = getClientIp(request);

        if (path.endsWith("/auth/login")) {
            Bucket bucket = loginBuckets.computeIfAbsent(ip, k -> newBucket(10));
            if (!bucket.tryConsume(1)) {
                rejectRequest(response, "Muitas tentativas de login. Aguarde 1 minuto.");
                return;
            }
        } else if (path.endsWith("/auth/esqueci-senha")) {
            Bucket bucket = esqueciSenhaBuckets.computeIfAbsent(ip, k -> newBucket(5));
            if (!bucket.tryConsume(1)) {
                rejectRequest(response, "Muitas solicitações de recuperação de senha. Aguarde 1 minuto.");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private Bucket newBucket(int requestsPerMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private void rejectRequest(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":429,\"message\":\"" + message + "\"}");
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
