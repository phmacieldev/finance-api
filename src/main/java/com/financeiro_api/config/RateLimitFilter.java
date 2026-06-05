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
 * - /auth/login:                    10 requisições por minuto
 * - /auth/register:                  5 requisições por minuto
 * - /auth/esqueci-senha:             5 requisições por minuto
 * - /auth/reenviar-verificacao:      5 requisições por minuto
 *
 * O IP é lido do header X-Real-IP (setado pelo proxy/load balancer),
 * com fallback para X-Forwarded-For e por último o IP direto da conexão.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> loginBuckets      = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets   = new ConcurrentHashMap<>();
    private final Map<String, Bucket> esqueciSenhaBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> reenviarBuckets   = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip   = getClientIp(request);

        if (path.endsWith("/auth/login")) {
            if (!loginBuckets.computeIfAbsent(ip, k -> newBucket(10)).tryConsume(1)) {
                rejectRequest(response, "Muitas tentativas de login. Aguarde 1 minuto.");
                return;
            }
        } else if (path.endsWith("/auth/register")) {
            if (!registerBuckets.computeIfAbsent(ip, k -> newBucket(5)).tryConsume(1)) {
                rejectRequest(response, "Muitos cadastros do mesmo IP. Aguarde 1 minuto.");
                return;
            }
        } else if (path.endsWith("/auth/esqueci-senha")) {
            if (!esqueciSenhaBuckets.computeIfAbsent(ip, k -> newBucket(5)).tryConsume(1)) {
                rejectRequest(response, "Muitas solicitações de recuperação de senha. Aguarde 1 minuto.");
                return;
            }
        } else if (path.endsWith("/auth/reenviar-verificacao")) {
            if (!reenviarBuckets.computeIfAbsent(ip, k -> newBucket(5)).tryConsume(1)) {
                rejectRequest(response, "Muitos reenvios solicitados. Aguarde 1 minuto.");
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
        // X-Real-IP é setado pelo proxy/load balancer e não pode ser forjado pelo cliente
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        // X-Forwarded-For como fallback — usamos o último IP da cadeia (mais confiável)
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
