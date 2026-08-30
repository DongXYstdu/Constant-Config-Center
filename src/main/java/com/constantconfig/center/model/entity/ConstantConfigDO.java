package com.constantconfig.center.model.entity;

import com.constantconfig.center.model.ConstantConfigValueType;

import java.time.LocalDateTime;

/**
 * 常量配置存储数据载体（DO/Entity）
 *
 * <p>对应 {@code constant_config_center} 表的一行记录，仅在存储 SPI 与实现间传递，
 * 不代表对外读写契约（写用 {@code ConfigSaveReqVO}、读用 {@code ConfigRespVO}）。</p>
 */
public class ConstantConfigDO {

    /** 主键 */
    private Long id;

    /** 分类ID（关联 constant_config_category.category_id） */
    private Long categoryId;

    /** 常量配置名称（全局唯一，对应 config_name 列） */
    private String configName;

    /** 键（对应 key 列，程序取值用） */
    private String key;

    /** 值（STRING 为文本；LIST / MAP 为 JSON 文本） */
    private String value;

    /** 值类型，默认 STRING */
    private ConstantConfigValueType valueType = ConstantConfigValueType.STRING;

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

    public ConstantConfigValueType getValueType() {
        return valueType;
    }

    public void setValueType(ConstantConfigValueType valueType) {
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

    /** 逐字段拷贝（供缓存写入前作防御性快照，避免共享可变对象被外部篡改污染） */
    public ConstantConfigDO copy() {
        ConstantConfigDO copy = new ConstantConfigDO();
        copy.id = this.id;
        copy.categoryId = this.categoryId;
        copy.configName = this.configName;
        copy.key = this.key;
        copy.value = this.value;
        copy.valueType = this.valueType;
        copy.version = this.version;
        copy.remark = this.remark;
        copy.createTime = this.createTime;
        copy.updateTime = this.updateTime;
        return copy;
    }
}