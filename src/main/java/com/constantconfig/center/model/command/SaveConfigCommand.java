package com.constantconfig.center.model.command;

import com.constantconfig.center.model.ConstantConfigValueType;

/**
 * 新增 / 更新配置的写命令
 *
 * <p>承载写操作入参，不含存储侧维护的 {@code id} / {@code version} / 时间列。
 * {@code value} 采用单一 {@link Object}：STRING 传 {@link String}，LIST / MAP 传集合 / 映射对象，
 * 由门面按 {@code valueType} 统一序列化。</p>
 *
 * <p>更新语义：{@code value} 为 {@code null} 表示不修改该字段（保留原值，由存储层合并补丁）。
 * 其它字段按非空覆盖。</p>
 */
public class SaveConfigCommand {

    /** 分类ID；{@code null} 时新增默认归属（properties.defaultCategoryId），更新时忽略 */
    private Long categoryId;

    /** 常量配置名称（全局唯一） */
    private String configName;

    /** 键（程序取值用，更新时作为定位键） */
    private String key;

    /** 值（STRING 传 String；LIST / MAP 传集合 / 映射对象），更新时 {@code null} 表示不修改 */
    private Object value;

    /** 值类型，默认 STRING */
    private ConstantConfigValueType valueType = ConstantConfigValueType.STRING;

    /** 备注 */
    private String remark;

    /** 期望版本（仅更新时使用，作为乐观并发 CAS 的期望版本）；新增时忽略，由存储层从 0 起 */
    private Long version;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public ConstantConfigValueType getValueType() {
        return valueType;
    }

    public void setValueType(ConstantConfigValueType valueType) {
        this.valueType = valueType;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}