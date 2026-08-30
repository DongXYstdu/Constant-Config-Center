package com.constantconfig.center.cache;

import com.constantconfig.center.event.CategoryChangedEvent;
import com.constantconfig.center.event.ConfigChangeType;
import com.constantconfig.center.event.ConfigChangedEvent;
import org.springframework.context.event.EventListener;

/**
 * 本地缓存失效监听
 *
 * <p>消费门面发布的变更事件，按需失效 {@link ConstantConfigCache}：</p>
 * <ul>
 *   <li>配置更新（{@link ConfigChangeType#UPDATED}）：值或名称可能变化且旧名称不可预判，
 *       采用整体失效，保证 {@code key} / {@code config_name} 双索引一致。</li>
 *   <li>配置新增 / 删除（{@code CREATED}/{@code DELETED}）：按 {@code key}（+ 可回填的名称）精确失效。</li>
 *   <li>分类变更：整体失效分类树。</li>
 * </ul>
 */
public class CacheInvalidationListener {

    private final ConstantConfigCache cache;

    public CacheInvalidationListener(ConstantConfigCache cache) {
        this.cache = cache;
    }

    @EventListener
    public void onConfigChanged(ConfigChangedEvent event) {
        if (event.getChangeType() == ConfigChangeType.UPDATED) {
            cache.clearConfig();
            return;
        }
        cache.invalidateConfig(event.getKey(), event.getConfigName());
    }

    @EventListener
    public void onCategoryChanged(CategoryChangedEvent event) {
        cache.clearCategory();
    }
}