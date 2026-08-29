package com.constantconfig.center.api;

import com.constantconfig.center.core.ConstantConfigCenterCategory;
import com.constantconfig.center.core.ConstantConfigCenterConflictException;
import com.constantconfig.center.core.ConstantConfigCenterValueType;

import java.util.List;
import java.util.Map;

/**
 * 常量配置中心门面接口（对外唯一入口）
 *
 * <p>对接方通过依赖注入使用本接口，屏蔽背后存储细节。</p>
 *
 * <p>读取与删除一律以 {@code key} 为唯一定位键（{@code key} 全局唯一）；{@code config_name}
 * （常量配置名称）同样全局唯一，可通过 {@link #getKeyByConfigName(String)} 由名称反查 key。
 * 带 {@code categoryId} 的重载中，该参数仅作兼容保留、不参与定位，分类维度用于罗列
 * （{@link #getAllConfig(Long)}）与组织。</p>
 *
 * <p>写入（setConfig 系列）为纯新增：{@code config_name} 或 {@code key} 任一全局唯一键已被占用时，
 * 抛出 {@link ConstantConfigCenterConflictException}（携带已存在行的主键 id），不做静默覆盖。</p>
 *
 * <p>API 首期返回 {@code String} 单值；LIST / MAP 类型通过泛型方法
 * {@link #getConfig(Long, String, Class)} 反序列化获取。</p>
 */
public interface ConstantConfigCenter {

    /** 默认分类ID，对应 {@code constant_config_category} 中 category_id=1 的根分类 */
    Long DEFAULT_CATEGORY_ID = 1L;

    /**
     * 按默认分类取值（STRING 类型直接返回文本；LIST / MAP 返回 JSON 文本）
     *
     * @param key 键
     * @return 配置值；不存在时返回 {@code null}
     */
    String getConfig(String key);

    /**
     * 按默认分类取值，带默认值
     *
     * @param key 键
     * @param defaultValue 配置不存在时的默认值
     * @return 配置值；不存在时返回 {@code defaultValue}
     */
    String getConfig(String key, String defaultValue);

    /**
     * 按默认分类泛型取值
     *
     * @param key 键
     * @param type 期望返回类型（LIST 传 {@link List}{@code .class}，MAP 传 {@link Map}{@code .class}）
     * @param <T> 返回类型
     * @return 反序列化后的配置值；不存在时返回 {@code null}
     */
    <T> T getConfig(String key, Class<T> type);

    /**
     * 按分类 + 键取值，带默认值
     *
     * @param categoryId 分类ID（key 全局唯一，本参数仅作兼容保留，不参与定位）
     * @param key 键
     * @param defaultValue 配置不存在时的默认值
     * @return 配置值；不存在时返回 {@code defaultValue}
     */
    String getConfig(Long categoryId, String key, String defaultValue);

    /**
     * 按分类 + 键泛型取值
     *
     * @param categoryId 分类ID
     * @param key 键
     * @param type 期望返回类型（STRING 传 {@link String}{@code .class}）
     * @param <T> 返回类型
     * @return 反序列化后的配置值；不存在时返回 {@code null}
     * @throws IllegalArgumentException 配置值不是合法 JSON，无法反序列化时抛出
     */
    <T> T getConfig(Long categoryId, String key, Class<T> type);

    /**
     * 按常量配置名称反查 key（{@code config_name} 全局唯一）
     *
     * @param configName 常量配置名称
     * @return 对应的键；名称不存在时返回 {@code null}
     */
    String getKeyByConfigName(String configName);

    /**
     * 按分类查询该分类下全部配置，扁平为 {@code Map<String,String>}（key → value）
     *
     * @param categoryId 分类ID
     * @return 键值映射；无配置时返回空 Map（非 null）
     */
    Map<String, String> getAllConfig(Long categoryId);

    /**
     * 按默认分类写入 STRING 配置（纯新增，冲突抛异常）
     *
     * @param configName 常量配置名称（全局唯一，冲突时抛异常）
     * @param key 键
     * @param value 值
     * @throws ConstantConfigCenterConflictException {@code config_name} 或 {@code key} 已被占用时抛出（携带已存在行主键 id）
     */
    void setConfig(String configName, String key, String value);

    /**
     * 按分类写入 STRING 配置（纯新增，冲突抛异常）
     *
     * @param categoryId 分类ID
     * @param configName 常量配置名称（全局唯一，冲突时抛异常）
     * @param key 键
     * @param value 值
     * @throws ConstantConfigCenterConflictException {@code config_name} 或 {@code key} 已被占用时抛出（携带已存在行主键 id）
     */
    void setConfig(Long categoryId, String configName, String key, String value);

    /**
     * 按分类写入 STRING 配置，带备注（纯新增，冲突抛异常）
     *
     * @param categoryId 分类ID
     * @param configName 常量配置名称（全局唯一，冲突时抛异常）
     * @param key 键
     * @param value 值
     * @param remark 备注
     * @throws ConstantConfigCenterConflictException {@code config_name} 或 {@code key} 已被占用时抛出（携带已存在行主键 id）
     */
    void setConfig(Long categoryId, String configName, String key, String value, String remark);

    /**
     * 按默认分类写入带值类型的配置（纯新增，冲突抛异常）
     *
     * <p>LIST / MAP 类型由 {@code valueType} 指定，{@code value} 会被序列化为 JSON 文本存储。</p>
     *
     * @param configName 常量配置名称（全局唯一，冲突时抛异常）
     * @param key 键
     * @param value 值（STRING 传字符串；LIST 传 {@link List}；MAP 传 {@link Map}）
     * @param valueType 值类型
     * @param <T> 值类型
     * @throws IllegalArgumentException 值无法序列化为 JSON 时抛出
     * @throws ConstantConfigCenterConflictException {@code config_name} 或 {@code key} 已被占用时抛出（携带已存在行主键 id）
     */
    <T> void setConfig(String configName, String key, T value, ConstantConfigCenterValueType valueType);

    /**
     * 按分类写入带值类型的配置（纯新增，冲突抛异常）
     *
     * @param categoryId 分类ID
     * @param configName 常量配置名称（全局唯一，冲突时抛异常）
     * @param key 键
     * @param value 值（STRING 传字符串；LIST 传 {@link List}；MAP 传 {@link Map}）
     * @param valueType 值类型
     * @param <T> 值类型
     * @throws IllegalArgumentException 值无法序列化为 JSON 时抛出
     * @throws ConstantConfigCenterConflictException {@code config_name} 或 {@code key} 已被占用时抛出（携带已存在行主键 id）
     */
    <T> void setConfig(Long categoryId, String configName, String key, T value, ConstantConfigCenterValueType valueType);

    /**
     * 按默认分类删除配置
     *
     * @param key 键
     * @return 是否删除成功（配置不存在时返回 {@code false}）
     */
    boolean deleteConfig(String key);

    /**
     * 按分类删除配置
     *
     * @param categoryId 分类ID（key 全局唯一，本参数仅作兼容保留，不参与定位）
     * @param key 键
     * @return 是否删除成功（配置不存在时返回 {@code false}）
     */
    boolean deleteConfig(Long categoryId, String key);

    // ────────────────────── 分类管理 ──────────────────────

    /**
     * 新增分类（自动生成 path / level）
     *
     * @param categoryName 分类名称（唯一）
     * @param parentId 父分类ID；根分类传 0
     * @param sort 同级排序号，可为 {@code null}（默认 0）
     * @return 新分类ID
     * @throws IllegalArgumentException 父分类不存在或名称重复时抛出
     */
    Long createCategory(String categoryName, Long parentId, Integer sort);

    /**
     * 删除分类（仅允许删除无子分类的叶子分类）
     *
     * @param categoryId 分类ID
     * @return 是否删除成功（分类不存在时返回 {@code false}）
     * @throws IllegalArgumentException 分类存在子分类时抛出
     */
    boolean deleteCategory(Long categoryId);

    /**
     * 查询全部分类并组装为树形结构
     *
     * @return 根分类列表（含嵌套 {@code children}）；无数据时返回空列表（非 null）
     */
    List<ConstantConfigCenterCategory> listCategoryTree();
}
