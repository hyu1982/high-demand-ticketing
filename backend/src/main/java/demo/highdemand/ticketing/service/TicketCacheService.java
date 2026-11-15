package demo.highdemand.ticketing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.highdemand.ticketing.dto.TicketResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Service
public class TicketCacheService {

    public enum CacheStatus {
        HIT,
        MISS,
        UNAVAILABLE
    }

    public record CacheResult(CacheStatus status, String value) {
        public static CacheResult hit(String value) {
            return new CacheResult(CacheStatus.HIT, value);
        }

        public static CacheResult miss() {
            return new CacheResult(CacheStatus.MISS, null);
        }

        public static CacheResult unavailable() {
            return new CacheResult(CacheStatus.UNAVAILABLE, null);
        }
    }

    public enum LockStatus {
        ACQUIRED,
        NOT_ACQUIRED,
        UNAVAILABLE
    }

    public record LockResult(
            LockStatus status,
            String lockValue
    ) {
        public static LockResult acquired(String lockValue) {
            return new LockResult(
                    LockStatus.ACQUIRED,
                    lockValue
            );
        }

        public static LockResult notAcquired() {
            return new LockResult(
                    LockStatus.NOT_ACQUIRED,
                    null
            );
        }

        public static LockResult unavailable() {
            return new LockResult(
                    LockStatus.UNAVAILABLE,
                    null
            );
        }
    }

    private static final Duration CACHE_TTL =
            Duration.ofMinutes(5);

    private static final Duration LOCK_TTL =
            Duration.ofSeconds(10);

    private static final long LOCK_RETRY_DELAY_MS =
            50;

    private static final int MAX_LOCK_RETRIES =
            10;

    private static final String RELEASE_LOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TicketCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {

        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Bulkhead(
            name = "redisCacheGet",
            fallbackMethod = "redisBulkheadFallback"
    )
    @CircuitBreaker(
            name = "redisCache",
            fallbackMethod = "redisGetFallback"
    )
    public CacheResult get(String cacheKey) {

//        try {
//            Thread.sleep(2000);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new RuntimeException(
//                    "Thread interrupted while testing bulkhead",
//                    e
//            );
//        }

        String value = redisTemplate
                .opsForValue()
                .get(cacheKey);

        if (value == null) {
            return CacheResult.miss();
        }

        return CacheResult.hit(value);
    }

    private CacheResult redisGetFallback(
            String cacheKey,
            Throwable throwable) {

        System.out.println(
                "Redis unavailable. Falling back to SQL: "
                        + throwable.getMessage()
        );

        return CacheResult.unavailable();
    }

    private CacheResult redisBulkheadFallback(
            String cacheKey,
            Throwable throwable) {

        System.out.println(
                "Redis bulkhead is full. " +
                        "Loading ticket directly from SQL: " +
                        throwable.getMessage()
        );

        return CacheResult.unavailable();
    }

//    public String get(String cacheKey) {
//
//        try {
//            return redisTemplate
//                    .opsForValue()
//                    .get(cacheKey);
//
//        } catch (Exception e) {
//
//            System.out.println(
//                    "Redis unavailable. Falling back to SQL: "
//                            + e.getMessage()
//            );
//
//            return null;
//        }
//    }

    public void putTicket(String cacheKey, TicketResponse ticketResponse) {

        try {
            String json =
                    objectMapper.writeValueAsString(ticketResponse);

            try {
                redisTemplate.opsForValue()
                        .set(
                                cacheKey,
                                json,
                                CACHE_TTL
                        );

            } catch (Exception e) {

                System.out.println(
                        "Redis unavailable while populating cache. " +
                                "Returning ticket from SQL: " +
                                e.getMessage()
                );
            }

        } catch (JsonProcessingException e) {

            throw new RuntimeException(
                    "Failed to serialize ticket for cache",
                    e
            );
        }
    }

    @Bulkhead(
            name = "redisCacheLock",
            fallbackMethod = "lockBulkheadFallback"
    )
    @CircuitBreaker(
            name = "redisCache",
            fallbackMethod = "lockFallback"
    )
    public LockResult tryAcquireLock(String lockKey) {

        String lockValue = UUID.randomUUID().toString();

        Boolean lockAcquired =
                redisTemplate.opsForValue()
                        .setIfAbsent(
                                lockKey,
                                lockValue,
                                LOCK_TTL
                        );

        if (Boolean.TRUE.equals(lockAcquired)) {
            return LockResult.acquired(lockValue);
        }

        return LockResult.notAcquired();
    }

    private LockResult lockFallback(
            String lockKey,
            Throwable throwable) {

        System.out.println(
                "Redis unavailable during lock acquisition: "
                        + throwable.getMessage()
        );

        return LockResult.unavailable();
    }

    private LockResult lockBulkheadFallback(
            String lockKey,
            Throwable throwable) {

        System.out.println(
                "Redis bulkhead is full during lock acquisition. " +
                        "Loading ticket directly from SQL: " +
                        throwable.getMessage()
        );

        return LockResult.unavailable();
    }

    public void releaseLock(String lockKey, String lockValue) {
        try {

            Long releaseResult =
                    redisTemplate.execute(
                            new DefaultRedisScript<>(
                                    RELEASE_LOCK_SCRIPT,
                                    Long.class
                            ),
                            Collections.singletonList(lockKey),
                            lockValue
                    );

            System.out.println(
                    "Lock release result: " + releaseResult
            );

        } catch (Exception e) {

            System.out.println(
                    "Redis unavailable while releasing lock. " +
                            "Lock will expire via TTL: " +
                            e.getMessage()
            );

            throw e;
        }
    }

    public void evict(String cacheKey) {

        try {
            redisTemplate.delete(cacheKey);

            System.out.println("Redis cache evicted: " + cacheKey);

        } catch (Exception e) {
            System.out.println("Redis unavailable while evicting cache: " + e.getMessage());
        }
    }
}
