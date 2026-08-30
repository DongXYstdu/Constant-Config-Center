package com.constantconfig.center.exception;

/**
 * 常量配置唯一键冲突异常
 *
 * <p>写入配置时，若 {@code config_name} 或 {@code key} 任一全局唯一键已被其它记录占用，
 * 则本次更新失败并抛出本异常；通过 {@link #getExistingId()} 可拿到已存在行的主键 id，
 * 便于调用方定位冲突记录。</p>
 */
public class ConstantConfigConflictException extends ConstantConfigException {

    /** 已存在行的主键 id */
    private final Long existingId;

    /** 冲突字段：config_name 或 key */
    private final String conflictField;

    /** 冲突的具体值 */
    private final String conflictValue;

    public ConstantConfigConflictException(Long existingId, String conflictField, String conflictValue) {
        super("常量配置已存在，更新失败：字段 " + conflictField + " = " + conflictValue
                + "，已存在行主键 id = " + existingId);
        this.existingId = existingId;
        this.conflictField = conflictField;
        this.conflictValue = conflictValue;
    }

    public Long getExistingId() {
        return existingId;
    }

    public String getConflictField() {
        return conflictField;
    }

    public String getConflictValue() {
        return conflictValue;
    }
}