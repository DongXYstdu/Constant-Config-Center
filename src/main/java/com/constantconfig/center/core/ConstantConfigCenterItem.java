package com.constantconfig.center.core;

import java.time.LocalDateTime;

/**
 * 常量配置条目模型
 *
 * <p>对应 {@code constant_config_center} 表的一行记录，是 Provider 与数据库之间的数据载体。</p>
 *
 * <p>现数据形态为单值 String（{@code value}）；{@code valueType} 预留 LIST / MAP 场景，
 * 后续取值时按类型做 JSON 反序列化。</p>
 */
public class ConstantConfigCenterItem {

    /** 主键 */
    private Long id;

    /** 分类ID（关联 constant_config_category.category_id） */
    private Long categoryId;

    /** 常量配置名称（全局唯一，对应 config_name 列） */
    private String configName;

    /** 键（对应 key 列，程序取值用） */
    private String key;

    /** 值（STRING 场景直接载入字符串；LIST / MAP 场景为经 {@link #valueObject} 序列化后写入的 JSON 文本） */
    private String value;

    /** 写入载体（LIST / MAP 时传入集合/映射对象，由门面序列化为 JSON 存入 {@code value}；STRING 时忽略） */
    private Object valueObject;

    /** 值类型，默认 STRING */
    private ConstantConfigCenterValueType valueType = ConstantConfigCenterValueType.STRING;

    /** 版本号，用于乐观并发与变更识别 */
    private Long version = 0L;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Object getValueObject() {
        return valueObject;
    }

    public void setValueObject(Object valueObject) {
        this.valueObject = valueObject;
    }

    public ConstantConfigCenterValueType getValueType() {
        return valueType;
    }

    public void setValueType(ConstantConfigCenterValueType valueType) {
        this.valueType = valueType;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
