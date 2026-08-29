package com.constantconfig.center.core;

/**
 * 常量配置值类型
 *
 * <p>对应 {@code constant_config_center.value_type} 字段（STRING | LIST | MAP）。</p>
 */
public enum ConstantConfigCenterValueType {

    /** 单值字符串 */
    STRING,

    /** 列表（值以 JSON 文本存储） */
    LIST,

    /** 键值对（值以 JSON 文本存储） */
    MAP;

    /**
     * 按名称解析，忽略大小写；无法识别时回退为 {@link #STRING}
     *
     * @param name 数据库中的值类型字符串
     * @return 对应的枚举值
     */
    public static ConstantConfigCenterValueType of(String name) {
        if (name == null) {
            return STRING;
        }
        try {
            return ConstantConfigCenterValueType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return STRING;
        }
    }
}
