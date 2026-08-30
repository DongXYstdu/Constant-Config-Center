package com.constantconfig.center.service;

import com.constantconfig.center.api.ConstantConfigCenter;
import com.constantconfig.center.cache.ConstantConfigCache;
import com.constantconfig.center.model.CategoryTreeAssembler;
import com.constantconfig.center.model.codec.ValueCodec;
import com.constantconfig.center.model.command.CategorySaveReqVO;
import com.constantconfig.center.model.command.ConfigSaveReqVO;
import com.constantconfig.center.model.entity.ConstantConfigCategoryDO;
import com.constantconfig.center.model.entity.ConstantConfigDO;
import com.constantconfig.center.model.view.CategoryRespVO;
import com.constantconfig.center.model.view.ConfigRespVO;
import com.constantconfig.center.exception.ConstantConfigException;
import com.constantconfig.center.exception.ConstantConfigNotFoundException;
import com.constantconfig.center.event.CategoryChangedEvent;
import com.constantconfig.center.event.ConfigChangeType;
import com.constantconfig.center.event.ConfigChangedEvent;
import com.constantconfig.center.query.CategoryPageReqVO;
import com.constantconfig.center.query.ConfigPageReqVO;
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
import java.util.function.Function;

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
    public Long createConfig(ConfigSaveReqVO command) {
        // 门面层统一做非空/存在性校验：把原本落到 DB 的裸约束/外键异常（NOT NULL / FK）前移为语义异常
        String key = command.getKey();
        requireNotBlank(key, "key");
        requireNotBlank(command.getConfigName(), "configName");
        if (command.getValue() == null) {
            throw new ConstantConfigException("配置值不能为空：key=" + key);
        }
        Long categoryId = command.getCategoryId() != null
                ? command.getCategoryId() : properties.getDefaultCategoryId();
        requireCategoryExists(categoryId);

        ConstantConfigDO item = new ConstantConfigDO();
        item.setCategoryId(categoryId);
        item.setConfigName(command.getConfigName());
        item.setKey(key);
        item.setValueType(command.getValueType());
        // STRING 直接载入文本；LIST / MAP 先把集合/映射对象序列化为 JSON 文本存储
        item.setValue(valueCodec.encode(command.getValue(), command.getValueType()));
        item.setRemark(command.getRemark());
        Long id = configWrite.create(item);
        eventPublisher.publishEvent(ConfigChangedEvent.created(this, key, command.getConfigName()));
        return id;
    }

    @Override
    public void updateConfig(ConfigSaveReqVO command) {
        // 先读当前快照，在门面层完成「非空覆盖补丁合并」；存储层只接收完整快照做直更 + version CAS，
        // 不再让存储实现感知"value 为空表示保留原值"这条写业务规则
        String key = command.getKey();
        requireNotBlank(key, "key");
        if (command.getConfigName() != null) {
            requireNotBlank(command.getConfigName(), "configName");
        }
        ConstantConfigDO existing = configRead.get(key);
        if (existing == null) {
            throw new ConstantConfigNotFoundException("key", key);
        }
        // 显式指定了新的分类时，校验其确实存在（避免落到 DB 外键异常）
        if (command.getCategoryId() != null && !command.getCategoryId().equals(existing.getCategoryId())) {
            requireCategoryExists(command.getCategoryId());
        }

        ConstantConfigDO snapshot = new ConstantConfigDO();
        snapshot.setKey(key); // 定位键，不可变
        snapshot.setCategoryId(command.getCategoryId() != null ? command.getCategoryId() : existing.getCategoryId());
        snapshot.setConfigName(command.getConfigName() != null ? command.getConfigName() : existing.getConfigName());
        snapshot.setRemark(command.getRemark() != null ? command.getRemark() : existing.getRemark());

        // 明确提供了新值时按 valueType 序列化；否则保留原值快照
        Object raw = command.getValue();
        if (raw != null) {
            snapshot.setValue(valueCodec.encode(raw, command.getValueType()));
            snapshot.setValueType(command.getValueType());
        } else {
            snapshot.setValue(existing.getValue());
            snapshot.setValueType(existing.getValueType());
        }

        // 乐观并发期望版本：显式传入则用之，否则以当前快照版本保底
        snapshot.setVersion(command.getVersion() != null ? command.getVersion() : existing.getVersion());

        if (!configWrite.update(snapshot)) {
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
    public List<ConfigRespVO> getConfigList(Long categoryId, String keyword) {
        return mapList(configRead.list(categoryId, keyword), this::toConfigRespVO);
    }

    @Override
    public PageResult<ConfigRespVO> getConfigPage(ConfigPageReqVO query) {
        Pagination pagination = Pagination.of(query.getPage(), query.getSize(), properties.getDefaultPageSize());
        long total = configRead.count(query.getCategoryId(), query.getKeyword());
        List<ConfigRespVO> views = mapList(configRead.listPage(
                query.getCategoryId(), query.getKeyword(), pagination.getOffset(), pagination.getSize()),
                this::toConfigRespVO);
        return PageResult.of(views, total, pagination.getPage(), pagination.getSize());
    }

    // ────────────────────── 分类管理 ──────────────────────

    @Override
    public Long createCategory(CategorySaveReqVO command) {
        // 门面层统一做非空 / 父分类存在性校验：把原本落到存储侧 / DB 的裸约束/外键异常前移为语义异常
        requireNotBlank(command.getCategoryName(), "categoryName");
        Long parentId = command.getCategoryParentId() == null ? 0L : command.getCategoryParentId();
        if (parentId != 0L) {
            requireCategoryExists(parentId);
        }
        ConstantConfigCategoryDO category = new ConstantConfigCategoryDO();
        category.setCategoryName(command.getCategoryName());
        category.setCategoryParentId(parentId);
        category.setSort(command.getSort() == null ? 0 : command.getSort());
        Long id = categoryWrite.create(category);
        eventPublisher.publishEvent(new CategoryChangedEvent(this, id, ConfigChangeType.CREATED));
        return id;
    }

    @Override
    public void updateCategory(CategorySaveReqVO command) {
        // 门面层统一做非空校验；分类ID为定位键必填，显式名称非空才允许更新
        if (command.getCategoryId() == null) {
            throw new ConstantConfigException("categoryId不能为空");
        }
        if (command.getCategoryName() != null) {
            requireNotBlank(command.getCategoryName(), "categoryName");
        }
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
        // 子分类预检保留：category_parent_id 无自引用外键，无法靠 FK 兜底，只能先查
        if (categoryRead.countChildren(categoryId) > 0) {
            throw new ConstantConfigException("分类存在子分类，无法删除：categoryId=" + categoryId);
        }
        // 分类下配置的存在性不再预读，改由外键 fk_ccc_category_id(ON DELETE RESTRICT) 在 DELETE 时原子拦截
        categoryWrite.delete(categoryId);
        eventPublisher.publishEvent(new CategoryChangedEvent(this, categoryId, ConfigChangeType.DELETED));
    }

    @Override
    public List<CategoryRespVO> getCategoryList(Long parentId, String keyword) {
        return mapList(categoryRead.list(parentId, keyword), this::toCategoryRespVO);
    }

    @Override
    public PageResult<CategoryRespVO> getCategoryPage(CategoryPageReqVO query) {
        Pagination pagination = Pagination.of(query.getPage(), query.getSize(), properties.getDefaultPageSize());
        long total = categoryRead.count(query.getParentId(), query.getKeyword());
        List<CategoryRespVO> views = mapList(categoryRead.listPage(
                query.getParentId(), query.getKeyword(), pagination.getOffset(), pagination.getSize()),
                this::toCategoryRespVO);
        return PageResult.of(views, total, pagination.getPage(), pagination.getSize());
    }

    @Override
    public List<CategoryRespVO> listCategoryTree() {
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
        return CategoryTreeAssembler.assemble(mapList(items, this::toCategoryRespVO));
    }

    // ────────────────────── DO → View 映射 ──────────────────────

    /**
     * 批量映射辅助：对源列表逐条调用映射器并收集结果，去重各列表/分页入口的「循环 + add + 组装」样板。
     *
     * @param source 源列表；为 {@code null} 时返回空列表
     * @param mapper 单条映射器
     * @param <S> 源元素类型
     * @param <T> 目标元素类型
     */
    private <S, T> List<T> mapList(List<S> source, Function<S, T> mapper) {
        if (source == null) {
            return new ArrayList<>();
        }
        List<T> result = new ArrayList<>(source.size());
        for (S item : source) {
            result.add(mapper.apply(item));
        }
        return result;
    }

    /** 配置 DO → 配置读视图（不含 version / 时间等存储侧字段） */
    private ConfigRespVO toConfigRespVO(ConstantConfigDO item) {
        ConfigRespVO view = new ConfigRespVO();
        view.setId(item.getId());
        view.setCategoryId(item.getCategoryId());
        view.setConfigName(item.getConfigName());
        view.setKey(item.getKey());
        view.setValue(item.getValue());
        view.setValueType(item.getValueType());
        view.setRemark(item.getRemark());
        return view;
    }

    /** 分类 DO → 分类读视图 */
    private CategoryRespVO toCategoryRespVO(ConstantConfigCategoryDO item) {
        CategoryRespVO view = new CategoryRespVO();
        view.setCategoryId(item.getCategoryId());
        view.setCategoryParentId(item.getCategoryParentId());
        view.setCategoryName(item.getCategoryName());
        view.setPath(item.getPath());
        view.setLevel(item.getLevel());
        view.setSort(item.getSort());
        return view;
    }

    // ────────────────────── 门面层校验辅助 ──────────────────────

    /** 必填字段非空断言：空/纯空白字符串抛语义异常，避免落到 DB 触发裸约束异常 */
    private void requireNotBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new ConstantConfigException(field + "不能为空");
        }
    }

    /** 分类存在性断言：分类必须存在，把外键约束异常前移为语义异常 */
    private void requireCategoryExists(Long categoryId) {
        if (categoryRead.get(categoryId) == null) {
            throw new ConstantConfigException("分类不存在：categoryId=" + categoryId);
        }
    }
}