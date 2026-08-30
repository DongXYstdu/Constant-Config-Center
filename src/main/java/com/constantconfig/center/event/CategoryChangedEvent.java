package com.constantconfig.center.event;

import org.springframework.context.ApplicationEvent;

/**
 * 分类变更事件
 *
 * <p>分类的增删改会影响层级与「分类树」缓存，事故处理采用粗粒度整体失效。
 * 分类无「按 key 定位」索引，故事件只携带 {@code categoryId} 与变更类型。</p>
 */
public class CategoryChangedEvent extends ApplicationEvent {

    /** 分类ID */
    private final Long categoryId;

    /** 变更类型 */
    private final ConfigChangeType changeType;

    public CategoryChangedEvent(Object source, Long categoryId, ConfigChangeType changeType) {
        super(source);
        this.categoryId = categoryId;
        this.changeType = changeType;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public ConfigChangeType getChangeType() {
        return changeType;
    }
}