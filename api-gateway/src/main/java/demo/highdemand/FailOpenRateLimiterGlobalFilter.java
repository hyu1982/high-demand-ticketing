package demo.highdemand;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class FailOpenRateLimiterGlobalFilter
        implements GlobalFilter, Ordered {

    private static final Logger log =
            (Logger) LoggerFactory.getLogger(FailOpenRateLimiterGlobalFilter.class);

    private static final int REPLENISH_RATE = 10;
    private static final int BURST_CAPACITY = 10;
    private static final int REQUESTED_TOKENS = 1;

    private static final Duration REDIS_TIMEOUT =
            Duration.ofMillis(300);

    //local ttl = math.max(1, math.ceil((capacity / rate) * 2))
    private static final DefaultRedisScript<List> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>("""
                local tokens_key = KEYS[1]
                local timestamp_key = KEYS[2]

                local rate = tonumber(ARGV[1])
                local capacity = tonumber(ARGV[2])
                local requested = tonumber(ARGV[3])
                local now = tonumber(ARGV[4])
                local ttl = 150

                local last_tokens =
                    tonumber(redis.call('GET', tokens_key)) or capacity

                local last_refreshed =
                    tonumber(redis.call('GET', timestamp_key)) or now

                local delta = math.max(0, now - last_refreshed)
                local filled = math.min(capacity, last_tokens + delta * rate)

                local allowed = 0
                local new_tokens = filled

                if filled >= requested then
                    allowed = 1
                    new_tokens = filled - requested
                end

                redis.call('SET', tokens_key, new_tokens, 'EX', ttl)
                redis.call('SET', timestamp_key, now, 'EX', ttl)

                return {allowed, new_tokens}
                """, List.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final KeyResolver keyResolver;

    public FailOpenRateLimiterGlobalFilter(
            ReactiveStringRedisTemplate redisTemplate,
            @Qualifier("ipKeyResolver") KeyResolver keyResolver) {

        this.redisTemplate = redisTemplate;
        this.keyResolver = keyResolver;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {

        String path = exchange.getRequest().getPath().value();

        // Apply rate limiting only to ticket API requests.
        if (!path.startsWith("/api/v1/tickets")) {
            return chain.filter(exchange);
        }

        Mono<Boolean> allowed = keyResolver.resolve(exchange)
                .flatMap(clientIp -> {
                    String prefix =
                            "gateway:rate-limit:{" + clientIp + "}";

                    List<String> keys = List.of(
                            prefix + ":tokens",
                            prefix + ":timestamp"
                    );

                    double now =
                            System.currentTimeMillis() / 1000.0;

                    return redisTemplate.execute(
                                    RATE_LIMIT_SCRIPT,
                                    keys,
                                    String.valueOf(REPLENISH_RATE),
                                    String.valueOf(BURST_CAPACITY),
                                    String.valueOf(REQUESTED_TOKENS),
                                    String.valueOf(now)
                            )
                            .timeout(REDIS_TIMEOUT)
                            .next()
                            .map(result ->
                                    result != null
                                            && !result.isEmpty()
                                            && ((Number) result.get(0)).longValue() == 1L
                            );
                })
                .onErrorResume(error -> {
                    log.warn(
                            "Rate limiter unavailable; allowing request through. "
                                    + "Path: {}, cause: {}",
                            path,
                            error.toString()
                    );

                    return Mono.just(true);
                });

        return allowed.flatMap(isAllowed -> {
            if (isAllowed) {
                return chain.filter(exchange);
            }

            exchange.getResponse().setStatusCode(
                    HttpStatus.TOO_MANY_REQUESTS
            );

            return exchange.getResponse().setComplete();
        });
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
