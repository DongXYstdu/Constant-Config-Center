package com.constantconfig.center.model;

import com.constantconfig.center.exception.ConstantConfigException;

/**
 * 常量配置值类型
 *
 * <p>对应 {@code constant_config_center.value_type} 字段（STRING | LIST | MAP）。</p>
 */
public enum ConstantConfigValueType {

    /** 单值字符串 */
    STRING,

    /** 列表（值以 JSON 文本存储） */
    LIST,

    /** 键值对（值以 JSON 文本存储） */
    MAP;

    /**
     * 按名称解析，忽略大小写；无法识别时回退为 {@link #STRING}
     *
     * <p>宽松容错版本：适合防御性/兜底场景（例如字段可空、外部输入不可靠）。
     * 若需要发现数据异常并快速失败，请使用 {@link #ofStrict(String)}。</p>
     *
     * @param name 数据库中的值类型字符串
     * @return 对应的枚举值
     */
    public static ConstantConfigValueType of(String name) {
        if (name == null) {
            return STRING;
        }
        try {
            return ConstantConfigValueType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return STRING;
        }
    }

    /**
     * 按名称严格解析，忽略大小写；无法识别时抛 {@link ConstantConfigException}
     *
     * <p>{@code null} / 空白透传为 {@link #STRING}（对应列 NOT NULL DEFAULT 'STRING' 的语义），
     * 其余任何无法识别的字符串立即抛出，避免脏数据被静默当作 STRING 掩盖。</p>
     *
     * @param name 数据库中的值类型字符串
     * @return 对应的枚举值
     * @throws ConstantConfigException 存在无法识别的值类型时抛出
     */
    public static ConstantConfigValueType ofStrict(String name) {
        if (name == null || name.trim().isEmpty()) {
            return STRING;
        }
        try {
            return ConstantConfigValueType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ConstantConfigException("无法识别的配置值类型：" + name, e);
        }
    }
}