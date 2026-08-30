package com.constantconfig.center.cache;

import com.constantconfig.center.model.entity.ConstantConfigCategoryDO;
import com.constantconfig.center.model.entity.ConstantConfigDO;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 读侧内存缓存（自研 TTL 缓存，零新增依赖）
 *
 * <p>仅缓存高频「单点读取」：配置按 {@code key} / 按 {@code config_name} 反查，以及「分类树」源（全部分类）。
 * 列表 / 分页本方案刻意不做缓存（失效粒度粗、易脏读），保持直查。</p>
 *
 * <p><b>一致性策略</b>：写操作由门面发布变更事件，本机 {@link CacheInvalidationListener}
 * 按事件失效对应索引；TTL 作为兜底，用于收敛「其它实例 / 直插 DB」导致的跨服务脏读窗口。</p>
 *
 * <p><b>边界</b>：不做负缓存（查询不存在不写入），规避「未命中 vs 值为空」的二义性。</p>
 */
public class ConstantConfigCache {

    private static final String PREFIX_CONFIG_KEY = "cfg:key:";
    private static final String PREFIX_CONFIG_NAME = "cfg:name:";
    private static final String KEY_CATEGORY_ALL = "cat:all";

    /** TTL 毫秒 */
    private final long ttlMillis;

    /** 容量上限（{@code >0} 生效；超出时停止写入新缓存，读落到 DB） */
    private final int maxSize;

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public ConstantConfigCache(long ttlSeconds, int maxSize) {
        this.ttlMillis = ttlSeconds * 1000L;
        this.maxSize = maxSize;
    }

    // ────────────────────── 配置：按 key / 按 config_name ──────────────────────

    public ConstantConfigDO getConfigByKey(String key) {
        Entry entry = get(PREFIX_CONFIG_KEY + key);
        return entry == null ? null : (ConstantConfigDO) entry.value;
    }

    public void putConfigByKey(String key, ConstantConfigDO value) {
        put(PREFIX_CONFIG_KEY + key, value);
    }

    public ConstantConfigDO getConfigByName(String configName) {
        Entry entry = get(PREFIX_CONFIG_NAME + configName);
        return entry == null ? null : (ConstantConfigDO) entry.value;
    }

    public void putConfigByName(String configName, ConstantConfigDO value) {
        put(PREFIX_CONFIG_NAME + configName, value);
    }

    /** 失效某条配置的两个索引（{@code configName} 为 null 时仅失效 key 索引） */
    public void invalidateConfig(String key, String configName) {
        if (key != null) {
            store.remove(PREFIX_CONFIG_KEY + key);
        }
        if (configName != null) {
            store.remove(PREFIX_CONFIG_NAME + configName);
        }
    }

    /** 失效全部配置索引（值或名称不可预判时使用） */
    public void clearConfig() {
        store.keySet().removeIf(k -> k.startsWith(PREFIX_CONFIG_KEY) || k.startsWith(PREFIX_CONFIG_NAME));
    }

    // ────────────────────── 分类：树（全部分类源） ──────────────────────

    @SuppressWarnings("unchecked")
    public List<ConstantConfigCategoryDO> getCategoryAll() {
        Entry entry = get(KEY_CATEGORY_ALL);
        return entry == null ? null : (List<ConstantConfigCategoryDO>) entry.value;
    }

    public void putCategoryAll(List<ConstantConfigCategoryDO> value) {
        put(KEY_CATEGORY_ALL, value);
    }

    /** 分类增删改影响层级与树，采用整体失效 */
    public void clearCategory() {
        store.keySet().removeIf(KEY_CATEGORY_ALL::equals);
    }

    // ────────────────────── 内部存取 ──────────────────────

    private Entry get(String cacheKey) {
        Entry entry = store.get(cacheKey);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt <= System.currentTimeMillis()) {
            store.remove(cacheKey, entry);
            return null;
        }
        return entry;
    }

    private void put(String cacheKey, Object value) {
        if (maxSize > 0 && store.size() >= maxSize) {
            return;
        }
        store.put(cacheKey, new Entry(value, System.currentTimeMillis() + ttlMillis));
    }

    private static final class Entry {
        final Object value;
        final long expireAt;

        Entry(Object value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }
}