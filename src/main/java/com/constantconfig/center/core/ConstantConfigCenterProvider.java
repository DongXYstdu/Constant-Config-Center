package com.constantconfig.center.core;

import java.util.List;

/**
 * 常量配置存储后端 SPI（扩展点）
 *
 * <p>其它项目可自行实现本接口并注册为 Spring Bean，替换或叠加默认的 JDBC 存储后端。</p>
 *
 * <p><b>职责边界</b>：本接口只负责配置数据的存储读写，返回 {@link ConstantConfigCenterItem} 模型；
 * 值类型转换（如 LIST / MAP 的 JSON 反序列化）由上层门面 {@code ConstantConfigCenter} 负责。</p>
 */
public interface ConstantConfigCenterProvider {

    /**
     * 按键查询配置条目（{@code key} 全局唯一）
     *
     * @param categoryId 分类ID（key 全局唯一，本参数仅作兼容保留，不参与定位）
     * @param key 键
     * @return 配置条目；不存在时返回 {@code null}
     */
    ConstantConfigCenterItem get(Long categoryId, String key);

    /**
     * 按常量配置名称查询（{@code config_name} 全局唯一，用于名称反查 key）
     *
     * @param configName 常量配置名称
     * @return 配置条目；不存在时返回 {@code null}
     */
    ConstantConfigCenterItem getByConfigName(String configName);

    /**
     * 按分类查询该分类下的全部配置条目
     *
     * @param categoryId 分类ID
     * @return 配置条目列表；不存在时返回空列表（非 null）
     */
    List<ConstantConfigCenterItem> list(Long categoryId);

    /**
     * 新增配置条目（纯新增，不做静默覆盖）
     *
     * <p>{@code id} 无需设置，由存储层维护。</p>
     *
     * @param item 配置条目
     * @throws ConstantConfigCenterConflictException {@code config_name} 或 {@code key} 任一全局唯一键
     *         已被其它记录占用时抛出，可通过 {@link ConstantConfigCenterConflictException#getExistingId()}
     *         获取已存在行的主键 id
     */
    void save(ConstantConfigCenterItem item);

    /**
     * 删除配置条目（{@code key} 全局唯一）
     *
     * @param categoryId 分类ID（key 全局唯一，本参数仅作兼容保留，不参与定位）
     * @param key 键
     * @return 是否删除成功（配置不存在时返回 {@code false}）
     */
    boolean delete(Long categoryId, String key);
}
