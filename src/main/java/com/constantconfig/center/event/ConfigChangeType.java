package com.constantconfig.center.event;

/**
 * 常量配置中心变更类型（写操作对外发布的语义）
 */
public enum ConfigChangeType {

    /** 新增 */
    CREATED,

    /** 更新 */
    UPDATED,

    /** 删除 */
    DELETED
}