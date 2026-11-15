package demo.highdemand.ticketing.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/redis")
public class RedisController {
    private final StringRedisTemplate redisTemplate;

    public RedisController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/test")
    public String testRedis() {

        redisTemplate
                .opsForValue()
                .set("disney:test", "Redis is working");

        return redisTemplate
                .opsForValue()
                .get("disney:test");
    }
}
