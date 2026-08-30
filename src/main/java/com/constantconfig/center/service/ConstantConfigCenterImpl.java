package com.constantconfig.center.service;

import com.constantconfig.center.api.ConstantConfigCenter;
import com.constantconfig.center.model.ConstantConfig;
import com.constantconfig.center.model.ConstantConfigCategory;
import com.constantconfig.center.model.ConstantConfigValueType;
import com.constantconfig.center.exception.ConstantConfigException;
import com.constantconfig.center.exception.ConstantConfigNotFoundException;
import com.constantconfig.center.exception.ConstantConfigSerializationException;
import com.constantconfig.center.query.CategoryPageQuery;
import com.constantconfig.center.query.ConfigPageQuery;
import com.constantconfig.center.query.PageResult;
import com.constantconfig.center.spi.ConstantConfigCategoryProvider;
import com.constantconfig.center.spi.ConstantConfigProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 常量配置中心门面默认实现
 *
 * <p>职责：编排 {@link ConstantConfigProvider}（配置存储）与
 * {@link ConstantConfigCategoryProvider}（分类存储），并按值类型处理——STRING 直接返回文本、
 * LIST / MAP 用 Jackson 序列化（写）与反序列化（读）；分类树在内存组装。本身不包含任何存储逻辑。</p>
 *
 * <p>读写删以 {@code key} 定位；{@code update} / {@code delete} 依据存储层返回的存在性，
 * 不存在时转抛 {@link ConstantConfigNotFoundException}。</p>
 */
public class ConstantConfigCenterImpl implements ConstantConfigCenter {

    private final ConstantConfigProvider provider;
    private final ConstantConfigCategoryProvider categoryProvider;
    private final ObjectMapper objectMapper;

    public ConstantConfigCenterImpl(ConstantConfigProvider provider,
                                    ConstantConfigCategoryProvider categoryProvider,
                                    ObjectMapper objectMapper) {
        this.provider = provider;
        this.categoryProvider = categoryProvider;
        this.objectMapper = objectMapper;
    }

    // ────────────────────── 业务取值（读取） ──────────────────────

    @Override
    public String getConfig(String key) {
        ConstantConfig item = provider.get(key);
        return item == null ? null : item.getValue();
    }

    @Override
    public String getConfig(String key, String defaultValue) {
        String value = getConfig(key);
        return value == null ? defaultValue : value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key, TypeReference<T> typeRef) {
        ConstantConfig item = provider.get(key);
        if (item == null || item.getValue() == null) {
            return null;
        }
        // 目标类型即 String：直接返回原文，避免无谓的 JSON 解析
        if (typeRef.getType() == String.class) {
            return (T) item.getValue();
        }
        try {
            return objectMapper.readValue(item.getValue(), typeRef);
        } catch (JsonProcessingException e) {
            throw new ConstantConfigSerializationException(
                    "配置值无法反序列化为目标类型：key=" + key, e);
        }
    }

    @Override
    public String getKeyByConfigName(String configName) {
        ConstantConfig item = provider.getByConfigName(configName);
        return item == null ? null : item.getKey();
    }

    // ────────────────────── 配置管理 CRUD ──────────────────────

    @Override
    public Long createConfig(ConstantConfig item) {
        if (item.getCategoryId() == null) {
            item.setCategoryId(DEFAULT_CATEGORY_ID);
        }
        if (item.getValueType() == null) {
            item.setValueType(ConstantConfigValueType.STRING);
        }
        // STRING 直接载入文本；LIST / MAP 需要先把集合/映射对象序列化为 JSON 文本存储
        Object raw = item.getValueObject() != null ? item.getValueObject() : item.getValue();
        item.setValue(serialize(raw, item.getValueType()));
        return provider.create(item);
    }

    @Override
    public void updateConfig(ConstantConfig item) {
        // 明确指定 LIST / MAP 且提供了值时，先序列化为 JSON 文本；否则交存储层做合并补丁（null 保留原值）
        if (item.getValueType() == ConstantConfigValueType.LIST
                || item.getValueType() == ConstantConfigValueType.MAP) {
            Object raw = item.getValueObject() != null ? item.getValueObject() : item.getValue();
            if (raw != null) {
                item.setValue(serialize(raw, item.getValueType()));
            }
        }
        if (!provider.update(item)) {
            throw new ConstantConfigNotFoundException("key", item.getKey());
        }
    }

    @Override
    public void deleteConfig(String key) {
        if (!provider.delete(key)) {
            throw new ConstantConfigNotFoundException("key", key);
        }
    }

    @Override
    public List<ConstantConfig> getConfigList(Long categoryId, String keyword) {
        return provider.list(categoryId, keyword);
    }

    @Override
    public PageResult<ConstantConfig> getConfigPage(ConfigPageQuery query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        int offset = (page - 1) * size;
        long total = provider.count(query.getCategoryId(), query.getKeyword());
        List<ConstantConfig> list = provider.listPage(query.getCategoryId(), query.getKeyword(), offset, size);
        return PageResult.of(list, total, page, size);
    }

    // ────────────────────── 分类管理 ──────────────────────

    @Override
    public Long createCategory(ConstantConfigCategory category) {
        return categoryProvider.create(category);
    }

    @Override
    public void updateCategory(ConstantConfigCategory category) {
        if (!categoryProvider.update(category)) {
            throw new ConstantConfigNotFoundException("categoryId", category.getCategoryId());
        }
    }

    @Override
    public void deleteCategory(Long categoryId) {
        if (categoryProvider.get(categoryId) == null) {
            throw new ConstantConfigNotFoundException("categoryId", categoryId);
        }
        if (categoryProvider.countChildren(categoryId) > 0) {
            throw new ConstantConfigException("分类存在子分类，无法删除：categoryId=" + categoryId);
        }
        long configCount = provider.countByCategory(categoryId);
        if (configCount > 0) {
            throw new ConstantConfigException(
                    "分类下存在 " + configCount + " 条配置，请先清空或迁移后再删除：categoryId=" + categoryId);
        }
        categoryProvider.delete(categoryId);
    }

    @Override
    public List<ConstantConfigCategory> getCategoryList(Long parentId, String keyword) {
        return categoryProvider.list(parentId, keyword);
    }

    @Override
    public PageResult<ConstantConfigCategory> getCategoryPage(CategoryPageQuery query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        int offset = (page - 1) * size;
        long total = categoryProvider.count(query.getParentId(), query.getKeyword());
        List<ConstantConfigCategory> list =
                categoryProvider.listPage(query.getParentId(), query.getKeyword(), offset, size);
        return PageResult.of(list, total, page, size);
    }

    @Override
    public List<ConstantConfigCategory> listCategoryTree() {
        List<ConstantConfigCategory> all = categoryProvider.list(null, null);
        Map<Long, ConstantConfigCategory> byId = new LinkedHashMap<>();
        for (ConstantConfigCategory category : all) {
            byId.put(category.getCategoryId(), category);
        }
        List<ConstantConfigCategory> roots = new ArrayList<>();
        for (ConstantConfigCategory category : all) {
            ConstantConfigCategory parent = byId.get(category.getCategoryParentId());
            if (parent != null) {
                parent.getChildren().add(category);
            } else {
                roots.add(category);
            }
        }
        return roots;
    }

    // ────────────────────── 内部工具 ──────────────────────

    /**
     * 按值类型序列化：STRING 直接存文本；LIST / MAP 序列化为 JSON 文本
     */
    private String serialize(Object value, ConstantConfigValueType valueType) {
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

    private int normalizePage(int page) {
        return page < 1 ? 1 : page;
    }

    private int normalizeSize(int size) {
        return size < 1 ? 10 : size;
    }
}