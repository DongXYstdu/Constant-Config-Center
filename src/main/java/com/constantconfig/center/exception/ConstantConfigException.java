package com.constantconfig.center.exception;

/**
 * 常量配置业务异常基类
 *
 * <p>统一 {@link IllegalArgumentException} 家族，使各类业务异常保持「非法参数」语义，
 * 同时允许调用方以「先捕基类、再捕子类」的方式区分处理。</p>
 *
 * <p>直接子类：{@link ConstantConfigConflictException}（唯一键冲突）、
 * {@link ConstantConfigNotFoundException}（目标不存在）、
 * {@link ConstantConfigSerializationException}（值序列化 / 反序列化失败）。</p>
 */
public class ConstantConfigException extends IllegalArgumentException {

    public ConstantConfigException(String message) {
        super(message);
    }

    public ConstantConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}