package com.constantconfig.center.core;

/**
 * 常量配置中心业务异常基类
 *
 * <p>统一 {@link IllegalArgumentException} 家族，使各类业务异常保持「非法参数」语义，
 * 同时允许调用方以「先捕基类、再捕子类」的方式区分处理。</p>
 *
 * <p>直接子类：{@link ConstantConfigCenterConflictException}（唯一键冲突）、
 * {@link ConstantConfigCenterNotFoundException}（目标不存在）、
 * {@link ConstantConfigCenterSerializationException}（值序列化 / 反序列化失败）。</p>
 */
public class ConstantConfigCenterException extends IllegalArgumentException {

    public ConstantConfigCenterException(String message) {
        super(message);
    }

    public ConstantConfigCenterException(String message, Throwable cause) {
        super(message, cause);
    }
}