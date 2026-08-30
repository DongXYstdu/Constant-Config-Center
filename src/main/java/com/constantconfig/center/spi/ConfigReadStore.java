package com.constantconfig.center.spi;

import com.constantconfig.center.model.entity.ConstantConfigDO;

import java.util.List;

/**
 * 常量配置读取侧存储 SPI（读扩展点）
 *
 * <p>与 {@link ConfigWriteStore} 分离，使自定义后端可按需只实现「读」或「写」一侧，
 * 无需实现全部方法。读取方法均为只读，无副作用。</p>
 *
 * @see ConfigWriteStore
 */
public interface ConfigReadStore {

    /**
     * 按键查询配置条目（{@code key} 全局唯一）
     *
     * @param key 键
     * @return 配置条目；不存在时返回 {@code null}
     */
    ConstantConfigDO get(String key);

    /**
     * 按常量配置名称查询（{@code config_name} 全局唯一，用于名称反查与去重）
     *
     * @param configName 常量配置名称
     * @return 配置条目；不存在时返回 {@code null}
     */
    ConstantConfigDO getByConfigName(String configName);

    /**
     * 配置列表（可选按分类 + 关键字过滤，命中全部）
     *
     * @param categoryId 分类ID；{@code null} 查询全部
     * @param keyword 关键字，模糊匹配 key / config_name；{@code null} / 空则不过滤
     * @return 配置条目列表；无数据时返回空列表（非 null）
     */
    List<ConstantConfigDO> list(Long categoryId, String keyword);

    /**
     * 配置分页查询（按下标范围返回命中记录的某一页）
     *
     * @param categoryId 分类ID；{@code null} 查询全部
     * @param keyword 关键字，模糊匹配 key / config_name；{@code null} / 空则不过滤
     * @param offset 起始行偏移（从 0 开始）
     * @param limit 返回行数上限
     * @return 该页配置条目列表；无数据时返回空列表（非 null）
     */
    List<ConstantConfigDO> listPage(Long categoryId, String keyword, int offset, int limit);

    /**
     * 按过滤条件统计配置条目的命中总数
     *
     * @param categoryId 分类ID；{@code null} 查询全部
     * @param keyword 关键字，模糊匹配 key / config_name；{@code null} / 空则不过滤
     * @return 命中总数
     */
    long count(Long categoryId, String keyword);

    /**
     * 统计某个分类下挂载的配置条数（用于删除分类前的完整性校验）
     *
     * @param categoryId 分类ID
     * @return 该分类下的配置条数（0 表示可安全删除）
     */
    long countByCategory(Long categoryId);
}