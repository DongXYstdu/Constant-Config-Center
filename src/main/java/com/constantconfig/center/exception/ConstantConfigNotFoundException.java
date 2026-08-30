package com.constantconfig.center.exception;

/**
 * 常量配置目标不存在异常
 *
 * <p>{@code updateConfig} / {@code deleteConfig} 时按定位键（如 {@code key}）未找到目标记录，
 * 或分类相关操作引用不存在的分类时抛出；携带用于定位的字段名与字段值，便于调用方排查。</p>
 */
public class ConstantConfigNotFoundException extends ConstantConfigException {

    /** 定位字段名，如 key / configName / categoryId */
    private final String locateField;

    /** 定位字段值 */
    private final Object locateValue;

    public ConstantConfigNotFoundException(String locateField, Object locateValue) {
        super("常量配置中心目标不存在：字段 " + locateField + " = " + locateValue);
        this.locateField = locateField;
        this.locateValue = locateValue;
    }

    public String getLocateField() {
        return locateField;
    }

    public Object getLocateValue() {
        return locateValue;
    }
}