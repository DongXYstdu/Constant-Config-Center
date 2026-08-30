package com.constantconfig.center.model.view;

import com.constantconfig.center.model.ConstantConfigValueType;

/**
 * 常量配置响应视图
 *
 * <p>面向调用方的只读返回对象，来自存储 DO 的映射；
 * 不含存储侧维护的 {@code version} / {@code createTime} / {@code updateTime}。
 * 乐观并发所需版本由调用方结合业务另行维护，或省略由门面按当前快照版本兜底。</p>
 */
public class ConfigRespVO {

    private Long id;
    private Long categoryId;
    private String configName;
    private String key;
    private String value;
    private ConstantConfigValueType valueType;
    private String remark;

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
}