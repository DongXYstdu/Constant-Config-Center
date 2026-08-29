package com.constantconfig.center.core;

import java.util.List;

/**
 * 常量配置分类存储后端 SPI（扩展点）
 *
 * <p>其它项目可自行实现本接口并注册为 Spring Bean，替换或叠加默认的 JDBC 分类存储后端。</p>
 *
 * <p><b>职责边界</b>：只负责分类数据的存储读写；{@code path} / {@code level} 的生成逻辑
 * 收敛在存储层（新增分类时依据父分类计算并回填），上层调用方只需传名称、父分类与排序号。</p>
 */
public interface ConstantConfigCenterCategoryProvider {

    /**
     * 按分类ID查询
     *
     * @param categoryId 分类ID
     * @return 分类；不存在时返回 {@code null}
     */
    ConstantConfigCenterCategory get(Long categoryId);

    /**
     * 按分类名称查询（{@code category_name} 全局唯一）
     *
     * @param categoryName 分类名称
     * @return 分类；不存在时返回 {@code null}
     */
    ConstantConfigCenterCategory getByCategoryName(String categoryName);

    /**
     * 查询全部分类（按 path 排序，保证父在前）
     *
     * @return 分类列表；无数据时返回空列表（非 null）
     */
    List<ConstantConfigCenterCategory> list();

    /**
     * 新增分类并自动生成 path / level
     *
     * @param category 分类（需提供 {@code categoryName}、{@code categoryParentId}、{@code sort}）
     * @return 新分类ID
     * @throws IllegalArgumentException 父分类不存在或名称重复时抛出
     */
    Long save(ConstantConfigCenterCategory category);

    /**
     * 删除分类
     *
     * @param categoryId 分类ID
     * @return 是否删除成功（分类不存在时返回 {@code false}）
     */
    boolean delete(Long categoryId);

    /**
     * 统计指定分类下的直接子分类数量
     *
     * @param categoryId 分类ID
     * @return 子分类数量
     */
    long countChildren(Long categoryId);
}
