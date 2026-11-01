package io.cvvexxx.gatewayservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

//Не больше capacity запросов за window на одного юзера (sub из JWT) или, без токена, на один IP.
//Счётчик в Redis, чтобы лимит был общий для всех инстансов gateway
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    //INCR и выставление TTL одной атомарной командой, иначе ключ может остаться без срока жизни
    //Можно сделать sliding window если этого будет мало
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final long capacity;
    private final Duration window;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public RateLimitFilter(StringRedisTemplate redis, long capacity, Duration window,
                           HandlerExceptionResolver handlerExceptionResolver) {
        this.redis = redis;
        this.capacity = capacity;
        this.window = window;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long count;
        try {
            count = redis.execute(INCREMENT, List.of("rate-limit:" + clientKey(request)),
                    String.valueOf(window.toMillis()));
        } catch (DataAccessException e) {
            //Redis лёг: пропускаем запрос, чтобы вместе с ним не лёг весь gateway
            log.warn("Rate limit skipped, Redis unavailable: {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        if (count != null && count > capacity) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(window.toSeconds()));
            handlerExceptionResolver.resolveException(request, response, null, new RateLimitExceededException());
            return;
        }
        chain.doFilter(request, response);
    }

    //Фильтр стоит после проверки токена, так что тут уже либо JWT, либо ничего
    private static String clientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return "user:" + jwt.getName();
        }
        //TODO(если будет nginx или балансировщик, то включить server.forward-headers-strategy=native.
        return "ip:" + request.getRemoteAddr();
    }
}
