package com.constantconfig.center.event;

import org.springframework.context.ApplicationEvent;

/**
 * 配置（键值项）变更事件
 *
 * <p>写操作（新增 / 更新 / 删除）成功后由门面发布，供本地缓存失效与本服务的跨服务监听消费。</p>
 *
 * <p>定位键为 {@code key}；{@link #getConfigName()} 在删除场景由门面尽量回填，
 * 用于失效「按名称反查 key」的缓存索引（无法回填时为 {@code null}）。</p>
 */
public class ConfigChangedEvent extends ApplicationEvent {

    /** 键（全局唯一，读写删定位键） */
    private final String key;

    /** 常量配置名称（删除等场景可能为 null） */
    private final String configName;

    /** 变更类型 */
    private final ConfigChangeType changeType;

    public ConfigChangedEvent(Object source, String key, String configName, ConfigChangeType changeType) {
        super(source);
        this.key = key;
        this.configName = configName;
        this.changeType = changeType;
    }

    public static ConfigChangedEvent created(Object source, String key, String configName) {
        return new ConfigChangedEvent(source, key, configName, ConfigChangeType.CREATED);
    }

    public static ConfigChangedEvent updated(Object source, String key, String configName) {
        return new ConfigChangedEvent(source, key, configName, ConfigChangeType.UPDATED);
    }

    public static ConfigChangedEvent deleted(Object source, String key, String configName) {
        return new ConfigChangedEvent(source, key, configName, ConfigChangeType.DELETED);
    }

    public String getKey() {
        return key;
    }

    public String getConfigName() {
        return configName;
    }

    public ConfigChangeType getChangeType() {
        return changeType;
    }
}