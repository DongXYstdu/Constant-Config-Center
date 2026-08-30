package com.constantconfig.center.service;

import com.constantconfig.center.api.ConstantConfigCenter;
import com.constantconfig.center.cache.ConstantConfigCache;
import com.constantconfig.center.model.CategoryTreeAssembler;
import com.constantconfig.center.model.codec.ValueCodec;
import com.constantconfig.center.model.command.SaveCategoryCommand;
import com.constantconfig.center.model.command.SaveConfigCommand;
import com.constantconfig.center.model.entity.ConstantConfigCategoryDO;
import com.constantconfig.center.model.entity.ConstantConfigDO;
import com.constantconfig.center.model.view.CategoryView;
import com.constantconfig.center.model.view.ConfigView;
import com.constantconfig.center.exception.ConstantConfigException;
import com.constantconfig.center.exception.ConstantConfigNotFoundException;
import com.constantconfig.center.event.CategoryChangedEvent;
import com.constantconfig.center.event.ConfigChangeType;
import com.constantconfig.center.event.ConfigChangedEvent;
import com.constantconfig.center.query.CategoryPageQuery;
import com.constantconfig.center.query.ConfigPageQuery;
import com.constantconfig.center.query.PageResult;
import com.constantconfig.center.query.Pagination;
import com.constantconfig.center.properties.ConstantConfigProperties;
import com.constantconfig.center.spi.CategoryReadStore;
import com.constantconfig.center.spi.CategoryWriteStore;
import com.constantconfig.center.spi.ConfigReadStore;
import com.constantconfig.center.spi.ConfigWriteStore;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

/**
 * 常量配置中心门面默认实现
 *
 * <p>职责：编排 {@link ConfigReadStore} / {@link ConfigWriteStore}（配置读写）与
 * {@link CategoryReadStore} / {@link CategoryWriteStore}（分类读写）；值类型序列化委托
 * {@link ValueCodec}，分类树组装委托 {@link CategoryTreeAssembler}。本实现是
 * Command / View 与存储 DO 之间的转换层，不包含任何存储逻辑。</p>
 *
 * <p>读写删以 {@code key} 定位；{@code update} / {@code delete} 依据存储层返回的存在性，
 * 不存在时转抛 {@link ConstantConfigNotFoundException}。</p>
 */
public class ConstantConfigCenterImpl implements ConstantConfigCenter {

    private final ConfigReadStore configRead;
    private final ConfigWriteStore configWrite;
    private final CategoryReadStore categoryRead;
    private final CategoryWriteStore categoryWrite;
    private final ValueCodec valueCodec;
    private final ConstantConfigProperties properties;
    private final ConstantConfigCache cache;
    private final ApplicationEventPublisher eventPublisher;

    public ConstantConfigCenterImpl(ConfigReadStore configRead,
                                    ConfigWriteStore configWrite,
                                    CategoryReadStore categoryRead,
                                    CategoryWriteStore categoryWrite,
                                    ValueCodec valueCodec,
                                    ConstantConfigProperties properties,
                                    ObjectProvider<ConstantConfigCache> cacheProvider,
                                    ApplicationEventPublisher eventPublisher) {
        this.configRead = configRead;
        this.configWrite = configWrite;
        this.categoryRead = categoryRead;
        this.categoryWrite = categoryWrite;
        this.valueCodec = valueCodec;
        this.properties = properties;
        this.cache = cacheProvider.getIfAvailable();
        this.eventPublisher = eventPublisher;
    }

    // ────────────────────── 业务取值（读取） ──────────────────────

    @Override
    public String getConfig(String key) {
        ConstantConfigDO item = readConfig(key);
        return item == null ? null : item.getValue();
    }

    @Override
    public String getConfig(String key, String defaultValue) {
        String value = getConfig(key);
        return value == null ? defaultValue : value;
    }

    @Override
    public <T> T getConfig(String key, TypeReference<T> typeRef) {
        ConstantConfigDO item = readConfig(key);
        if (item == null || item.getValue() == null) {
            return null;
        }
        return valueCodec.decode(item.getValue(), typeRef);
    }

    @Override
    public String getKeyByConfigName(String configName) {
        if (cache != null) {
            ConstantConfigDO cached = cache.getConfigByName(configName);
            if (cached != null) {
                return cached.getKey();
            }
        }
        ConstantConfigDO item = configRead.getByConfigName(configName);
        if (item != null && cache != null) {
            cache.putConfigByName(configName, item);
        }
        return item == null ? null : item.getKey();
    }

    /** 读配置：命中缓存直接返回，未命中回源 DB 并回填缓存（不做负缓存） */
    private ConstantConfigDO readConfig(String key) {
        if (cache != null) {
            ConstantConfigDO cached = cache.getConfigByKey(key);
            if (cached != null) {
                return cached;
            }
        }
        ConstantConfigDO item = configRead.get(key);
        if (item != null && cache != null) {
            cache.putConfigByKey(key, item);
        }
        return item;
    }

    // ────────────────────── 配置管理 CRUD ──────────────────────

    @Override
    public Long createConfig(SaveConfigCommand command) {
        ConstantConfigDO item = new ConstantConfigDO();
        item.setCategoryId(command.getCategoryId() != null
                ? command.getCategoryId() : properties.getDefaultCategoryId());
        item.setConfigName(command.getConfigName());
        item.setKey(command.getKey());
        item.setValueType(command.getValueType());
        // STRING 直接载入文本；LIST / MAP 先把集合/映射对象序列化为 JSON 文本存储
        item.setValue(valueCodec.encode(command.getValue(), command.getValueType()));
        item.setRemark(command.getRemark());
        Long id = configWrite.create(item);
        eventPublisher.publishEvent(ConfigChangedEvent.created(this, command.getKey(), command.getConfigName()));
        return id;
    }

    @Override
    public void updateConfig(SaveConfigCommand command) {
        ConstantConfigDO item = new ConstantConfigDO();
        item.setKey(command.getKey()); // 定位键，不可变
        item.setCategoryId(command.getCategoryId());
        item.setConfigName(command.getConfigName());
        item.setRemark(command.getRemark());
        item.setVersion(command.getVersion()); // 乐观并发期望版本（可为空，由存储层取当前版本保底）

        // 明确提供了新值时，按 valueType 序列化并随更新写入；value 为空表示保留原值
        Object raw = command.getValue();
        if (raw != null) {
            item.setValue(valueCodec.encode(raw, command.getValueType()));
            item.setValueType(command.getValueType());
        }
        if (!configWrite.update(item)) {
            throw new ConstantConfigNotFoundException("key", command.getKey());
        }
        // 值/名称/分类均可能变化，统一失效双索引（名称不可预判，交由监听整体失效）
        eventPublisher.publishEvent(ConfigChangedEvent.updated(this, command.getKey(), command.getConfigName()));
    }

    @Override
    public void deleteConfig(String key) {
        // 回填 config_name，用于失效「按名称反查 key」索引
        ConstantConfigDO existing = configRead.get(key);
        if (!configWrite.delete(key)) {
            throw new ConstantConfigNotFoundException("key", key);
        }
        String configName = existing != null ? existing.getConfigName() : null;
        eventPublisher.publishEvent(ConfigChangedEvent.deleted(this, key, configName));
    }

    @Override
    public List<ConfigView> getConfigList(Long categoryId, String keyword) {
        List<ConfigView> views = new ArrayList<>();
        for (ConstantConfigDO item : configRead.list(categoryId, keyword)) {
            views.add(toConfigView(item));
        }
        return views;
    }

    @Override
    public PageResult<ConfigView> getConfigPage(ConfigPageQuery query) {
        Pagination pagination = Pagination.of(query.getPage(), query.getSize(), properties.getDefaultPageSize());
        long total = configRead.count(query.getCategoryId(), query.getKeyword());
        List<ConfigView> views = new ArrayList<>();
        for (ConstantConfigDO item : configRead.listPage(
                query.getCategoryId(), query.getKeyword(), pagination.getOffset(), pagination.getSize())) {
            views.add(toConfigView(item));
        }
        return PageResult.of(views, total, pagination.getPage(), pagination.getSize());
    }

    // ────────────────────── 分类管理 ──────────────────────

    @Override
    public Long createCategory(SaveCategoryCommand command) {
        ConstantConfigCategoryDO category = new ConstantConfigCategoryDO();
        category.setCategoryName(command.getCategoryName());
        category.setCategoryParentId(command.getCategoryParentId() == null ? 0L : command.getCategoryParentId());
        category.setSort(command.getSort() == null ? 0 : command.getSort());
        Long id = categoryWrite.create(category);
        eventPublisher.publishEvent(new CategoryChangedEvent(this, id, ConfigChangeType.CREATED));
        return id;
    }

    @Override
    public void updateCategory(SaveCategoryCommand command) {
        ConstantConfigCategoryDO category = new ConstantConfigCategoryDO();
        category.setCategoryId(command.getCategoryId());
        category.setCategoryName(command.getCategoryName());
        category.setSort(command.getSort());
        if (!categoryWrite.update(category)) {
            throw new ConstantConfigNotFoundException("categoryId", command.getCategoryId());
        }
        eventPublisher.publishEvent(new CategoryChangedEvent(this, command.getCategoryId(), ConfigChangeType.UPDATED));
    }

    @Override
    public void deleteCategory(Long categoryId) {
        if (categoryRead.get(categoryId) == null) {
            throw new ConstantConfigNotFoundException("categoryId", categoryId);
        }
        if (categoryRead.countChildren(categoryId) > 0) {
            throw new ConstantConfigException("分类存在子分类，无法删除：categoryId=" + categoryId);
        }
        long configCount = configRead.countByCategory(categoryId);
        if (configCount > 0) {
            throw new ConstantConfigException(
                    "分类下存在 " + configCount + " 条配置，请先清空或迁移后再删除：categoryId=" + categoryId);
        }
        categoryWrite.delete(categoryId);
        eventPublisher.publishEvent(new CategoryChangedEvent(this, categoryId, ConfigChangeType.DELETED));
    }

    @Override
    public List<CategoryView> getCategoryList(Long parentId, String keyword) {
        List<CategoryView> views = new ArrayList<>();
        for (ConstantConfigCategoryDO item : categoryRead.list(parentId, keyword)) {
            views.add(toCategoryView(item));
        }
        return views;
    }

    @Override
    public PageResult<CategoryView> getCategoryPage(CategoryPageQuery query) {
        Pagination pagination = Pagination.of(query.getPage(), query.getSize(), properties.getDefaultPageSize());
        long total = categoryRead.count(query.getParentId(), query.getKeyword());
        List<CategoryView> views = new ArrayList<>();
        for (ConstantConfigCategoryDO item : categoryRead.listPage(
                query.getParentId(), query.getKeyword(), pagination.getOffset(), pagination.getSize())) {
            views.add(toCategoryView(item));
        }
        return PageResult.of(views, total, pagination.getPage(), pagination.getSize());
    }

    @Override
    public List<CategoryView> listCategoryTree() {
        List<ConstantConfigCategoryDO> items;
        if (cache != null) {
            List<ConstantConfigCategoryDO> cached = cache.getCategoryAll();
            if (cached != null) {
                items = cached;
            } else {
                items = categoryRead.list(null, null);
                cache.putCategoryAll(items);
            }
        } else {
            items = categoryRead.list(null, null);
        }
        List<CategoryView> views = new ArrayList<>();
        for (ConstantConfigCategoryDO item : items) {
            views.add(toCategoryView(item));
        }
        return CategoryTreeAssembler.assemble(views);
    }

    // ────────────────────── DO → View 映射 ──────────────────────

    /** 配置 DO → 配置读视图 */
    private ConfigView toConfigView(ConstantConfigDO item) {
        ConfigView view = new ConfigView();
        view.setId(item.getId());
        view.setCategoryId(item.getCategoryId());
        view.setConfigName(item.getConfigName());
        view.setKey(item.getKey());
        view.setValue(item.getValue());
        view.setValueType(item.getValueType());
        view.setRemark(item.getRemark());
        view.setVersion(item.getVersion());
        view.setCreateTime(item.getCreateTime());
        view.setUpdateTime(item.getUpdateTime());
        return view;
    }

    /** 分类 DO → 分类读视图 */
    private CategoryView toCategoryView(ConstantConfigCategoryDO item) {
        CategoryView view = new CategoryView();
        view.setCategoryId(item.getCategoryId());
        view.setCategoryParentId(item.getCategoryParentId());
        view.setCategoryName(item.getCategoryName());
        view.setPath(item.getPath());
        view.setLevel(item.getLevel());
        view.setSort(item.getSort());
        return view;
    }
}