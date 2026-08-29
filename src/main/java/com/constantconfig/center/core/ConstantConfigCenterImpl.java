package com.constantconfig.center.core;

import com.constantconfig.center.api.CategoryPageQuery;
import com.constantconfig.center.api.ConfigPageQuery;
import com.constantconfig.center.api.ConstantConfigCenter;
import com.constantconfig.center.api.PageResult;
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
 * <p>职责：编排 {@link ConstantConfigCenterProvider}（配置存储）与
 * {@link ConstantConfigCenterCategoryProvider}（分类存储），并按值类型处理——STRING 直接返回文本、
 * LIST / MAP 用 Jackson 序列化（写）与反序列化（读）；分类树在内存组装。本身不包含任何存储逻辑。</p>
 *
 * <p>读写删以 {@code key} 定位；{@code update} / {@code delete} 依据存储层返回的存在性，
 * 不存在时转抛 {@link ConstantConfigCenterNotFoundException}。</p>
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

    // ────────────────────── 业务取值（读取） ──────────────────────

    @Override
    public String getConfig(String key) {
        ConstantConfigCenterItem item = provider.get(key);
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
        ConstantConfigCenterItem item = provider.get(key);
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
            throw new ConstantConfigCenterSerializationException(
                    "配置值无法反序列化为目标类型：key=" + key, e);
        }
    }

    @Override
    public String getKeyByConfigName(String configName) {
        ConstantConfigCenterItem item = provider.getByConfigName(configName);
        return item == null ? null : item.getKey();
    }

    // ────────────────────── 配置管理 CRUD ──────────────────────

    @Override
    public Long createConfig(ConstantConfigCenterItem item) {
        if (item.getCategoryId() == null) {
            item.setCategoryId(DEFAULT_CATEGORY_ID);
        }
        if (item.getValueType() == null) {
            item.setValueType(ConstantConfigCenterValueType.STRING);
        }
        // STRING 直接载入文本；LIST / MAP 需要先把集合/映射对象序列化为 JSON 文本存储
        Object raw = item.getValueObject() != null ? item.getValueObject() : item.getValue();
        item.setValue(serialize(raw, item.getValueType()));
        return provider.create(item);
    }

    @Override
    public void updateConfig(ConstantConfigCenterItem item) {
        // 明确指定 LIST / MAP 且提供了值时，先序列化为 JSON 文本；否则交存储层做合并补丁（null 保留原值）
        if (item.getValueType() == ConstantConfigCenterValueType.LIST
                || item.getValueType() == ConstantConfigCenterValueType.MAP) {
            Object raw = item.getValueObject() != null ? item.getValueObject() : item.getValue();
            if (raw != null) {
                item.setValue(serialize(raw, item.getValueType()));
            }
        }
        if (!provider.update(item)) {
            throw new ConstantConfigCenterNotFoundException("key", item.getKey());
        }
    }

    @Override
    public void deleteConfig(String key) {
        if (!provider.delete(key)) {
            throw new ConstantConfigCenterNotFoundException("key", key);
        }
    }

    @Override
    public List<ConstantConfigCenterItem> getConfigList(Long categoryId, String keyword) {
        return provider.list(categoryId, keyword);
    }

    @Override
    public PageResult<ConstantConfigCenterItem> getConfigPage(ConfigPageQuery query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        int offset = (page - 1) * size;
        long total = provider.count(query.getCategoryId(), query.getKeyword());
        List<ConstantConfigCenterItem> list = provider.listPage(query.getCategoryId(), query.getKeyword(), offset, size);
        return PageResult.of(list, total, page, size);
    }

    // ────────────────────── 分类管理 ──────────────────────

    @Override
    public Long createCategory(ConstantConfigCenterCategory category) {
        return categoryProvider.create(category);
    }

    @Override
    public void updateCategory(ConstantConfigCenterCategory category) {
        if (!categoryProvider.update(category)) {
            throw new ConstantConfigCenterNotFoundException("categoryId", category.getCategoryId());
        }
    }

    @Override
    public void deleteCategory(Long categoryId) {
        if (categoryProvider.get(categoryId) == null) {
            throw new ConstantConfigCenterNotFoundException("categoryId", categoryId);
        }
        if (categoryProvider.countChildren(categoryId) > 0) {
            throw new ConstantConfigCenterException("分类存在子分类，无法删除：categoryId=" + categoryId);
        }
        categoryProvider.delete(categoryId);
    }

    @Override
    public List<ConstantConfigCenterCategory> getCategoryList(Long parentId, String keyword) {
        return categoryProvider.list(parentId, keyword);
    }

    @Override
    public PageResult<ConstantConfigCenterCategory> getCategoryPage(CategoryPageQuery query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        int offset = (page - 1) * size;
        long total = categoryProvider.count(query.getParentId(), query.getKeyword());
        List<ConstantConfigCenterCategory> list =
                categoryProvider.listPage(query.getParentId(), query.getKeyword(), offset, size);
        return PageResult.of(list, total, page, size);
    }

    @Override
    public List<ConstantConfigCenterCategory> listCategoryTree() {
        List<ConstantConfigCenterCategory> all = categoryProvider.list(null, null);
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

    // ────────────────────── 内部工具 ──────────────────────

    /**
     * 按值类型序列化：STRING 直接存文本；LIST / MAP 序列化为 JSON 文本
     */
    private String serialize(Object value, ConstantConfigCenterValueType valueType) {
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
                    throw new ConstantConfigCenterSerializationException(
                            "配置值无法序列化为 JSON，值类型 " + valueType, e);
                }
            default:
                throw new ConstantConfigCenterException("不支持的值类型：" + valueType);
        }
    }

    private int normalizePage(int page) {
        return page < 1 ? 1 : page;
    }

    private int normalizeSize(int size) {
        return size < 1 ? 10 : size;
    }
}