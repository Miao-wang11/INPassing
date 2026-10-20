package com.ikeu.server.annotation;

import java.lang.annotation.*;

/**
 * Redis 防护标记注解，标识方法使用了 Redis 缓存防护或分布式锁防护。
 *
 * <p>标记此注解的方法具备以下一种或多种防护能力：
 * <ul>
 *   <li>{@link Type#LOCK} — Redisson 分布式锁防并发</li>
 *   <li>{@link Type#CACHE_DEFEND} — {@code RedisDefendUtil} 缓存击穿+穿透防护</li>
 * </ul>
 *
 * @author ikeu
 * @since 2026/06/11
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisDefend {

    /** 防护类型，默认同时包含锁和缓存防护。 */
    Type[] value() default {Type.LOCK, Type.CACHE_DEFEND};

    enum Type {
        /** Redisson 分布式锁（防并发/击穿） */
        LOCK,
        /** Redis 缓存穿透+击穿防护（空标记 + 双检） */
        CACHE_DEFEND
    }
}
