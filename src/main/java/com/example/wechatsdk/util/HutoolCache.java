package com.example.wechatsdk.util;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;

/**
 * 基于 Hutool {@link TimedCache} 的进程内缓存，用来托管 access_token。
 *
 * <p>注意：这是单机缓存。多实例部署时各实例会各自刷新 token，
 * 生产环境建议替换为 Redis + 分布式锁。</p>
 */
public final class HutoolCache {

    /** 默认 7200 秒，实际过期时间由 {@link #set} 传入 */
    private static final TimedCache<String, String> TIMED_CACHE = CacheUtil.newTimedCache(7200_000L);

    static {
        // 每 5 秒清理一次过期条目（单位为毫秒）
        TIMED_CACHE.schedulePrune(5_000L);
    }

    private HutoolCache() {
    }

    public static String get(String key) {
        return TIMED_CACHE.get(key, false);
    }

    /**
     * @param ttlSeconds 过期时间，单位秒
     */
    public static void set(String key, String value, int ttlSeconds) {
        TIMED_CACHE.put(key, value, ttlSeconds * 1000L);
    }

}
