package com.constantconfig.center.model.codec;

import com.constantconfig.center.exception.ConstantConfigException;
import com.constantconfig.center.exception.ConstantConfigSerializationException;
import com.constantconfig.center.model.ConstantConfigValueType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 配置值编解码器
 *
 * <p>按 {@link ConstantConfigValueType} 把对象序列化为 JSON 文本（写）、把文本反序列化为目标类型（读）。
 * STRING 值无需 JSON 处理，直接透传。门面只把"值对象 / 文本"交给本类，不直接接触
 * {@link ObjectMapper} 与 {@link JsonProcessingException} 细节。</p>
 */
public class ValueCodec {

    private final ObjectMapper objectMapper;

    public ValueCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 按值类型序列化：STRING 直接落文本；LIST / MAP 序列化为 JSON 文本。
     *
     * @param value     待序列化的值对象（LIST / MAP）或文本（STRING）
     * @param valueType 值类型
     * @return 落库的文本（{@code value} 为空时为 {@code null}）
     */
    public String encode(Object value, ConstantConfigValueType valueType) {
        switch (valueType) {
            case STRING:
                return value == null ? null : (value instanceof String ? (String) value : String.valueOf(value));
            case LIST:
            case MAP:
                if (value == null) {
                    return null;
                }
                try {
                    return objectMapper.writeValueAsString(value);
                } catch (JsonProcessingException e) {
                    throw new ConstantConfigSerializationException(
                            "配置值无法序列化为 JSON，值类型 " + valueType, e);
                }
            default:
                throw new ConstantConfigException("不支持的值类型：" + valueType);
        }
    }

    /**
     * 将文本反序列化为目标类型。
     *
     * <p>目标类型即 {@code String} 时直接返回原文，避免无谓的 JSON 解析。</p>
     *
     * @param value   落库的文本
     * @param typeRef 目标类型引用（如 {@code new TypeReference<List<String>>() {}}）
     * @return 反序列化结果（{@code value} 为 {@code null} 时返回 {@code null}）
     */
    @SuppressWarnings("unchecked")
    public <T> T decode(String value, TypeReference<T> typeRef) {
        if (value == null) {
            return null;
        }
        if (typeRef.getType() == String.class) {
            return (T) value;
        }
        try {
            return objectMapper.readValue(value, typeRef);
        } catch (JsonProcessingException e) {
            throw new ConstantConfigSerializationException(
                    "配置值无法反序列化为目标类型", e);
        }
    }
}