package com.ikeu.server.util;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 缓存防护工具，将缓存击穿（互斥锁+双检）和缓存穿透（空标记）逻辑抽取为通用模板。
 *
 * <p>适用场景：高并发下需要保护数据库免受缓存击穿和穿透的热点数据查询。
 * 调用方只需提供缓存实例、键、锁参数和 DB 查询逻辑，无需重复编写防护代码。
 *
 * @author ikeu
 * @since 2026/06/04
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDefendUtil {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    /**
     * 通用缓存读取模板，集成击穿防护和穿透防护。
     *
     * <ol>
     *   <li>查缓存，命中直接返回</li>
     *   <li>查空标记（穿透防护），存在则返回 null</li>
     *   <li>获取分布式锁（击穿防护），持锁后双检缓存和空标记</li>
     *   <li>调用 dbLoader 查 DB，非 null 写缓存，null 写空标记</li>
     *   <li>未获取到锁则降级直查 DB（仍检查空标记防穿透）</li>
     * </ol>
     *
     * @param cache     Spring Cache 实例
     * @param cacheKey  缓存键
     * @param nullKey   空标记键，用于穿透防护
     * @param nullTtl   空标记过期时间（秒）
     * @param lockKey   分布式锁键，用于击穿防护
     * @param lockWait  锁等待时间（秒）
     * @param lockExpire 锁持有时间（秒）
     * @param type      返回值类型（支持泛型，通过 {@code new TypeReference<T>() {}.getType()} 传入）
     * @param dbLoader  DB 查询逻辑
     * @param <T>       返回值类型
     * @return 缓存或 DB 查询结果，null 表示数据不存在（已写入空标记）
     */
    public <T> T getOrLoad(Cache cache, String cacheKey,
                           String nullKey, Long nullTtl,
                           String lockKey, Long lockWait, Long lockExpire,
                           Type type, Supplier<T> dbLoader) {
        // 1. 查缓存
        T cached = getFromCache(cache, cacheKey, type);
        if (cached != null) {
            return cached;
        }

        // 2. 穿透防护：查空标记
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(nullKey))) {
            return null;
        }

        // 3. 击穿防护：获取分布式锁
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(lockWait, lockExpire, TimeUnit.SECONDS)) {
                // 双检缓存
                cached = getFromCache(cache, cacheKey, type);
                if (cached != null) {
                    return cached;
                }
                // 双检空标记
                if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(nullKey))) {
                    return null;
                }

                T result = dbLoader.get();
                if (result != null) {
                    if (cache != null) {
                        cache.put(cacheKey, JSONUtil.toJsonStr(result));
                    }
                } else {
                    stringRedisTemplate.opsForValue().set(nullKey, "1", nullTtl, TimeUnit.SECONDS);
                }
                return result;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        // 4. 未获取到锁，降级直查 DB
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(nullKey))) {
            return null;
        }
        T result = dbLoader.get();
        if (result == null) {
            stringRedisTemplate.opsForValue().set(nullKey, "1", nullTtl, TimeUnit.SECONDS);
        }
        return result;
    }

    private static <T> T getFromCache(Cache cache, String cacheKey, Type type) {
        if (cache == null) {
            return null;
        }
        Cache.ValueWrapper wrapper = cache.get(cacheKey);
        if (wrapper != null && wrapper.get() instanceof String json) {
            return JSONUtil.toBean(json, type, false);
        }
        return null;
    }
}
