package com.financeiro_api.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiting por IP nos endpoints de autenticação sensíveis.
 * - /auth/login:                    10 requisições por minuto
 * - /auth/register:                  5 requisições por minuto
 * - /auth/esqueci-senha:             5 requisições por minuto
 * - /auth/reenviar-verificacao:      5 requisições por minuto
 *
 * O IP é lido do header X-Real-IP (setado pelo proxy/load balancer),
 * com fallback para X-Forwarded-For e por último o IP direto da conexão.
 *
 * Limpeza de memória: entradas não acessadas há mais de 2 minutos são removidas
 * a cada 30 minutos via @Scheduled (evita memory leak em produção).
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Tempo máximo de inatividade antes de remover entrada da memória (2 minutos = 1 janela de rate limit + margem) */
    private static final long IDLE_EXPIRY_MS = Duration.ofMinutes(2).toMillis();

    /** Wrapper que mantém o bucket junto com o timestamp do último acesso */
    private record BucketEntry(Bucket bucket, AtomicLong lastAccessMs) {
        BucketEntry(Bucket bucket) {
            this(bucket, new AtomicLong(System.currentTimeMillis()));
        }

        Bucket touch() {
            lastAccessMs.set(System.currentTimeMillis());
            return bucket;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - lastAccessMs.get() > IDLE_EXPIRY_MS;
        }
    }

    private final Map<String, BucketEntry> loginBuckets        = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> registerBuckets     = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> esqueciSenhaBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> reenviarBuckets     = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip   = getClientIp(request);

        if (path.endsWith("/auth/login")) {
            if (!getBucket(loginBuckets, ip, 10).tryConsume(1)) {
                rejectRequest(response, "Muitas tentativas de login. Aguarde 1 minuto.");
                return;
            }
        } else if (path.endsWith("/auth/register")) {
            if (!getBucket(registerBuckets, ip, 5).tryConsume(1)) {
                rejectRequest(response, "Muitos cadastros do mesmo IP. Aguarde 1 minuto.");
                return;
            }
        } else if (path.endsWith("/auth/esqueci-senha")) {
            if (!getBucket(esqueciSenhaBuckets, ip, 5).tryConsume(1)) {
                rejectRequest(response, "Muitas solicitações de recuperação de senha. Aguarde 1 minuto.");
                return;
            }
        } else if (path.endsWith("/auth/reenviar-verificacao")) {
            if (!getBucket(reenviarBuckets, ip, 5).tryConsume(1)) {
                rejectRequest(response, "Muitos reenvios solicitados. Aguarde 1 minuto.");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private Bucket getBucket(Map<String, BucketEntry> map, String ip, int requestsPerMinute) {
        return map.computeIfAbsent(ip, k -> new BucketEntry(newBucket(requestsPerMinute))).touch();
    }

    /**
     * Limpeza de entradas inativas a cada 30 minutos.
     * Remove IPs que não fizeram requests nas últimas 2 janelas de rate-limit.
     */
    @Scheduled(fixedDelay = 1_800_000)
    public void limparEntradasExpiradas() {
        int antes = loginBuckets.size() + registerBuckets.size()
                + esqueciSenhaBuckets.size() + reenviarBuckets.size();

        evictExpired(loginBuckets);
        evictExpired(registerBuckets);
        evictExpired(esqueciSenhaBuckets);
        evictExpired(reenviarBuckets);

        int depois = loginBuckets.size() + registerBuckets.size()
                + esqueciSenhaBuckets.size() + reenviarBuckets.size();

        if (antes > depois) {
            log.debug("RateLimitFilter: removidas {} entradas expiradas ({} restantes)", antes - depois, depois);
        }
    }

    private void evictExpired(Map<String, BucketEntry> map) {
        Iterator<Map.Entry<String, BucketEntry>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired()) {
                it.remove();
            }
        }
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
