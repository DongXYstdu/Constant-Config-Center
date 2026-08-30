package com.constantconfig.center.api;

import com.constantconfig.center.model.command.CategorySaveReqVO;
import com.constantconfig.center.model.command.ConfigSaveReqVO;
import com.constantconfig.center.model.view.CategoryRespVO;
import com.constantconfig.center.model.view.ConfigRespVO;
import com.constantconfig.center.exception.ConstantConfigConflictException;
import com.constantconfig.center.exception.ConstantConfigException;
import com.constantconfig.center.exception.ConstantConfigNotFoundException;
import com.constantconfig.center.exception.ConstantConfigSerializationException;
import com.constantconfig.center.query.CategoryPageReqVO;
import com.constantconfig.center.query.ConfigPageReqVO;
import com.constantconfig.center.query.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

/**
 * 常量配置中心门面接口（对外唯一入口）
 *
 * <p><b>定位语义</b>：读取、更新、删除一律以 {@code key} 为全局唯一键定位，
 * 分类（category）仅用于罗列 / 分页过滤与层级组织。</p>
 *
 * <p><b>三类模型</b>：<ul>
 * <li>写命令 —— {@link ConfigSaveReqVO} / {@link CategorySaveReqVO}，承载新增 / 更新的入参；
 *     {@code value} 为单一 {@link Object}（STRING 传 {@code String}，LIST / MAP 传集合 / 映射对象），
 *     由门面按 {@code valueType} 统一序列化后落库。</li>
 * <li>读视图 —— {@link ConfigRespVO} / {@link CategoryRespVO}，对外只读返回，不含写入辅助字段。</li>
 * <li>业务取值 —— {@link #getConfig(String)} 按存储值类型召回：STRING 直接返回文本，
 *     LIST / MAP 返回 JSON 文本；需要反序列化为强类型时用 {@link #getConfig(String, TypeReference)}。</li>
 * </ul></p>
 *
 * <p><b>异常约定</b>（均为 {@link ConstantConfigException} 子类，
 * 间接继承 {@link IllegalArgumentException}）：唯一键冲突抛
 * {@link ConstantConfigConflictException}；更新 / 删除目标不存在抛
 * {@link ConstantConfigNotFoundException}；LIST / MAP 序列化失败抛
 * {@link ConstantConfigSerializationException}。</p>
 */
public interface ConstantConfigCenter {

    /**
     * 默认分类ID，对应 {@code constant_config_category} 中 category_id=1 的分类。
     *
     * @deprecated 默认分类 ID 已收敛到 {@code ConstantConfigProperties.defaultCategoryId}，
     *             实现统一从该属性取值；此处保留仅为兼容既有引用。
     */
    @Deprecated
    Long DEFAULT_CATEGORY_ID = 1L;

    // ────────────────────── 业务取值（读取） ──────────────────────

    /**
     * 按 key 取值（字符串形态）
     *
     * <p>STRING 类型直接返回文本；LIST / MAP 类型返回 JSON 文本。</p>
     *
     * @param key 键（全局唯一）
     * @return 配置值；不存在时返回 {@code null}
     */
    String getConfig(String key);

    /**
     * 按 key 取值，不存在时返回默认值（字符串形态）
     *
     * @param key 键
     * @param defaultValue 不存在时的默认值
     * @return 配置值或默认值
     */
    String getConfig(String key, String defaultValue);

    /**
     * 按 key 取值并反序列化为指定强类型（保留泛型）
     *
     * <p>取原值文本后，由 Jackson 按 {@code typeRef} 反序列化；适合 LIST / MAP / 自定义对象。
     * 例如：{@code List<String> list = ccc.getConfig("k", new TypeReference<List<String>>() {})}。</p>
     *
     * @param key 键
     * @param typeRef 目标类型引用（保留泛型）
     * @param <T> 返回类型
     * @return 反序列化后的值；不存在时返回 {@code null}
     * @throws ConstantConfigSerializationException 值无法反序列化为目标类型时抛出
     */
    <T> T getConfig(String key, TypeReference<T> typeRef);

    /**
     * 按常量配置名称反查 key（{@code config_name} 全局唯一）
     *
     * @param configName 常量配置名称
     * @return 对应的键；名称不存在时返回 {@code null}
     */
    String getKeyByConfigName(String configName);

    // ────────────────────── 配置管理 CRUD ──────────────────────

    /**
     * 新增配置（纯新增，冲突抛异常）
     *
     * <p>{@code value} 为单一 {@link Object}：STRING 传 {@code String}，LIST / MAP 传集合 / 映射对象，
     * 由门面按 {@code valueType} 统一序列化后落库；{@code id} / {@code version} / 时间列由存储层维护。</p>
     *
     * @param command 写命令（至少提供 configName、key、value）
     * @return 新记录主键 id
     * @throws ConstantConfigConflictException {@code config_name} 或 {@code key} 已被占用时抛出
     */
    Long createConfig(ConfigSaveReqVO command);

    /**
     * 更新配置（按 {@code key} 定位，不修改 {@code key} 本身）
     *
     * <p>可更新 {@code configName} / {@code value} / {@code valueType} / {@code remark} /
     * {@code categoryId}。更新语义为「非空覆盖」：{@code value} 为 {@code null} 表示不修改该字段
     * （保留原值）。{@code version} 作为可选期望版本参与乐观并发 CAS，
     * 与当前版本不一致时抛 {@link ConstantConfigVersionMismatchException}。</p>
     *
     * @param command 写命令（必须携带 {@code key} 作为定位键）
     * @throws ConstantConfigNotFoundException 目标 key 不存在时抛出
     * @throws ConstantConfigConflictException 新 configName 与其它记录冲突时抛出
     */
    void updateConfig(ConfigSaveReqVO command);

    /**
     * 删除配置（按 {@code key} 定位）
     *
     * @param key 键（全局唯一）
     * @throws ConstantConfigNotFoundException 目标 key 不存在时抛出
     */
    void deleteConfig(String key);

    /**
     * 配置列表（可选按分类 + 关键字过滤）
     *
     * @param categoryId 分类ID；{@code null} 查询全部
     * @param keyword 关键字，模糊匹配 key / config_name；{@code null} / 空则不过滤
     * @return 配置读视图列表；无数据时返回空列表（非 null）
     */
    List<ConfigRespVO> getConfigList(Long categoryId, String keyword);

    /**
     * 配置分页查询
     *
     * @param query 分页条件（categoryId / keyword / page / size）
     * @return 分页结果（含总数，元素为配置读视图）
     */
    PageResult<ConfigRespVO> getConfigPage(ConfigPageReqVO query);

    // ────────────────────── 分类管理 ──────────────────────

    /**
     * 新增分类（自动生成 path / level）
     *
     * <p>{@code categoryName}、{@code categoryParentId}、{@code sort} 由调用方提供；
     * {@code categoryId} / {@code path} / {@code level} 由存储层生成。</p>
     *
     * @param command 写命令
     * @return 新分类ID
     * @throws ConstantConfigException 父分类不存在或父级名称重复时抛出
     */
    Long createCategory(CategorySaveReqVO command);

    /**
     * 更新分类（按 {@code categoryId} 定位）
     *
     * <p>更新语义为「非空覆盖」：仅更新调用方提供的非空字段（{@code categoryName} / {@code sort}）。</p>
     *
     * @param command 写命令（必须携带 {@code categoryId}）
     * @throws ConstantConfigNotFoundException 目标分类不存在时抛出
     * @throws ConstantConfigException 名称被其它分类占用时抛出
     */
    void updateCategory(CategorySaveReqVO command);

    /**
     * 删除分类（仅允许删除无子分类的叶子分类）
     *
     * @param categoryId 分类ID
     * @throws ConstantConfigNotFoundException 目标分类不存在时抛出
     * @throws ConstantConfigException 分类存在子分类时抛出
     */
    void deleteCategory(Long categoryId);

    /**
     * 分类列表（可选按父ID + 关键字过滤）
     *
     * @param parentId 父分类ID；{@code null} 查询全部
     * @param keyword 关键字，模糊匹配 category_name；{@code null} / 空则不过滤
     * @return 分类读视图列表；无数据时返回空列表（非 null）
     */
    List<CategoryRespVO> getCategoryList(Long parentId, String keyword);

    /**
     * 分类分页查询
     *
     * @param query 分页条件（parentId / keyword / page / size）
     * @return 分页结果（含总数，元素为分类读视图）
     */
    PageResult<CategoryRespVO> getCategoryPage(CategoryPageReqVO query);

    /**
     * 查询全部分类并组装为树形结构
     *
     * @return 根分类读视图列表（含嵌套 {@code children}）；无数据时返回空列表（非 null）
     */
    List<CategoryRespVO> listCategoryTree();
}