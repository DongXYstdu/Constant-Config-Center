package com.constantconfig.center.core;

import com.constantconfig.center.api.ConstantConfigCenter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 常量配置中心门面默认实现
 *
 * <p>职责：调用 {@link ConstantConfigCenterProvider} 获取配置条目，并按值类型处理——
 * STRING 直接返回文本，LIST / MAP 用 Jackson 反序列化；分类管理委托
 * {@link ConstantConfigCenterCategoryProvider}。本身不包含任何存储逻辑。</p>
 */
public class ConstantConfigCenterImpl implements ConstantConfigCenter {

    private final ConstantConfigCenterProvider provider;
    private final ConstantConfigCenterCategoryProvider categoryProvider;
    private final ObjectMapper objectMapper;

    public ConstantConfigCenterImpl(ConstantConfigCenterProvider provider,
                                    ConstantConfigCenterCategoryProvider categoryProvider,
                                    ObjectMapper objectMapper) {
        this.provider = provider;
        this.categoryProvider = categoryProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getConfig(String key) {
        return getConfig(DEFAULT_CATEGORY_ID, key, (String) null);
    }

    @Override
    public String getConfig(String key, String defaultValue) {
        return getConfig(DEFAULT_CATEGORY_ID, key, defaultValue);
    }

    @Override
    public <T> T getConfig(String key, Class<T> type) {
        return getConfig(DEFAULT_CATEGORY_ID, key, type);
    }

    @Override
    public String getConfig(Long categoryId, String key, String defaultValue) {
        ConstantConfigCenterItem item = provider.get(categoryId, key);
        if (item == null || item.getValue() == null) {
            return defaultValue;
        }
        return item.getValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfig(Long categoryId, String key, Class<T> type) {
        ConstantConfigCenterItem item = provider.get(categoryId, key);
        if (item == null || item.getValue() == null) {
            return null;
        }
        // STRING 类型直接返回文本，避免无谓的 JSON 解析
        if (type == String.class) {
            return (T) item.getValue();
        }
        try {
            return objectMapper.readValue(item.getValue(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "配置值不是合法 JSON，无法反序列化为 " + type.getSimpleName()
                            + "：categoryId=" + categoryId + ", key=" + key, e);
        }
    }

    @Override
    public String getKeyByConfigName(String configName) {
        ConstantConfigCenterItem item = provider.getByConfigName(configName);
        return item == null ? null : item.getKey();
    }

    @Override
    public Map<String, String> getAllConfig(Long categoryId) {
        Map<String, String> result = new HashMap<>();
        for (ConstantConfigCenterItem item : provider.list(categoryId)) {
            if (item.getKey() != null) {
                result.put(item.getKey(), item.getValue());
            }
        }
        return result;
    }

    @Override
    public void setConfig(String configName, String key, String value) {
        setConfig(DEFAULT_CATEGORY_ID, configName, key, value, (String) null);
    }

    @Override
    public void setConfig(Long categoryId, String configName, String key, String value) {
        setConfig(categoryId, configName, key, value, (String) null);
    }

    @Override
    public void setConfig(Long categoryId, String configName, String key, String value, String remark) {
        ConstantConfigCenterItem item = new ConstantConfigCenterItem();
        item.setCategoryId(categoryId);
        item.setConfigName(configName);
        item.setKey(key);
        item.setValue(value);
        item.setValueType(ConstantConfigCenterValueType.STRING);
        item.setRemark(remark);
        provider.save(item);
    }

    @Override
    public <T> void setConfig(String configName, String key, T value, ConstantConfigCenterValueType valueType) {
        setConfig(DEFAULT_CATEGORY_ID, configName, key, value, valueType);
    }

    @Override
    public <T> void setConfig(Long categoryId, String configName, String key, T value,
                              ConstantConfigCenterValueType valueType) {
        ConstantConfigCenterItem item = new ConstantConfigCenterItem();
        item.setCategoryId(categoryId);
        item.setConfigName(configName);
        item.setKey(key);
        item.setValueType(valueType);
        item.setValue(serialize(value, valueType));
        provider.save(item);
    }

    @Override
    public boolean deleteConfig(String key) {
        return deleteConfig(DEFAULT_CATEGORY_ID, key);
    }

    @Override
    public boolean deleteConfig(Long categoryId, String key) {
        return provider.delete(categoryId, key);
    }

    @Override
    public Long createCategory(String categoryName, Long parentId, Integer sort) {
        ConstantConfigCenterCategory category = new ConstantConfigCenterCategory();
        category.setCategoryName(categoryName);
        category.setCategoryParentId(parentId == null ? 0L : parentId);
        category.setSort(sort);
        return categoryProvider.save(category);
    }

    @Override
    public boolean deleteCategory(Long categoryId) {
        if (categoryProvider.countChildren(categoryId) > 0) {
            throw new IllegalArgumentException("分类存在子分类，无法删除：categoryId=" + categoryId);
        }
        return categoryProvider.delete(categoryId);
    }

    @Override
    public List<ConstantConfigCenterCategory> listCategoryTree() {
        List<ConstantConfigCenterCategory> all = categoryProvider.list();
        Map<Long, ConstantConfigCenterCategory> byId = new LinkedHashMap<>();
        for (ConstantConfigCenterCategory category : all) {
            byId.put(category.getCategoryId(), category);
        }
        List<ConstantConfigCenterCategory> roots = new ArrayList<>();
        for (ConstantConfigCenterCategory category : all) {
            ConstantConfigCenterCategory parent = byId.get(category.getCategoryParentId());
            if (parent != null) {
                parent.getChildren().add(category);
            } else {
                roots.add(category);
            }
        }
        return roots;
    }

    /**
     * 按值类型序列化：STRING 直接存文本；LIST / MAP 序列化为 JSON 文本
     */
    private String serialize(Object value, ConstantConfigCenterValueType valueType) {
        switch (valueType) {
            case STRING:
                return value instanceof String ? (String) value : String.valueOf(value);
            case LIST:
            case MAP:
                try {
                    return objectMapper.writeValueAsString(value);
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException(
                            "配置值无法序列化为 JSON，类型 " + valueType.name(), e);
                }
            default:
                throw new IllegalArgumentException("不支持的值类型：" + valueType);
        }
    }
}
