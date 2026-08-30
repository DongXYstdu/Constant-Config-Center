package com.constantconfig.center.model.view;

import com.constantconfig.center.model.ConstantConfigValueType;

import java.time.LocalDateTime;

/**
 * 常量配置读视图
 *
 * <p>面向调用方的只读返回对象，来自存储 DO 的映射；不含写入辅助字段。</p>
 */
public class ConfigView {

    private Long id;
    private Long categoryId;
    private String configName;
    private String key;
    private String value;
    private ConstantConfigValueType valueType;
    private String remark;
    private Long version;
    private LocalDateTime createTime;
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