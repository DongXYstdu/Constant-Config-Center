package com.constantconfig.center.core;

/**
 * 常量配置值序列化 / 反序列化异常
 *
 * <p>LIST / MAP 类配置值写入时无法序列化为 JSON，或读取时无法按目标类型反序列化时抛出。
 * 延续 {@link IllegalArgumentException} 语义（项目历史约束）。</p>
 */
public class ConstantConfigCenterSerializationException extends ConstantConfigCenterException {

    public ConstantConfigCenterSerializationException(String message) {
        super(message);
    }

    public ConstantConfigCenterSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}