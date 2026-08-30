package com.constantconfig.center.spi;

import com.constantconfig.center.model.ConstantConfigCategory;
import com.constantconfig.center.exception.ConstantConfigException;

import java.util.List;

/**
 * 常量配置分类存储后端 SPI（扩展点）
 *
 * <p>其它项目可自行实现本接口并注册为 Spring Bean，替换或叠加默认的 JDBC 分类存储后端。</p>
 *
 * <p><b>职责边界</b>：只负责分类数据的存储读写；{@code path} / {@code level} 的生成逻辑
 * 收敛在存储层（新增/变更时依据父分类计算并回填），上层调用方只需传名称、父分类与排序号。</p>
 *
 * <p><b>原语约定</b>：与配置 SPI 对齐——{@code update} / {@code delete} 返回 {@code boolean}
 * 表示目标是否存在；{@code create} 遇父分类不存在或名称重复时抛
 * {@link ConstantConfigException}。</p>
 */
public interface ConstantConfigCategoryProvider {

    /**
     * 按分类ID查询
     *
     * @param categoryId 分类ID
     * @return 分类；不存在时返回 {@code null}
     */
    ConstantConfigCategory get(Long categoryId);

    /**
     * 按分类名称查询（{@code category_name} 全局唯一，用于名称反查与去重）
     *
     * @param categoryName 分类名称
     * @return 分类；不存在时返回 {@code null}
     */
    ConstantConfigCategory getByCategoryName(String categoryName);

    /**
     * 新增分类并自动生成 path / level
     *
     * @param category 分类（需提供 {@code categoryName}、{@code categoryParentId}、{@code sort}）
     * @return 新分类ID
     * @throws ConstantConfigException 父分类不存在或名称重复时抛出
     */
    Long create(ConstantConfigCategory category);

    /**
     * 更新分类（按 {@code categoryId} 定位）
     *
     * <p>可更新 {@code categoryName}、{@code sort}；父分类与 {@code path} / {@code level} 不随之变更。</p>
     *
     * @param category 分类（必须携带 {@code categoryId}）
     * @return 目标分类是否存在（不存在返回 {@code false}，由门面转抛异常）
     * @throws ConstantConfigException 名称被其它分类占用时抛出
     */
    boolean update(ConstantConfigCategory category);

    /**
     * 删除分类
     *
     * @param categoryId 分类ID
     * @return 是否删除成功（目标不存在返回 {@code false}，由门面转抛异常）
     */
    boolean delete(Long categoryId);

    /**
     * 按父ID + 关键字过滤查询分类列表（命中全部，按 path 排序保证父在前）
     *
     * @param parentId 父分类ID；{@code null} 查询全部
     * @param keyword 关键字，模糊匹配 category_name；{@code null} / 空则不过滤
     * @return 分类列表；无数据时返回空列表（非 null）
     */
    List<ConstantConfigCategory> list(Long parentId, String keyword);

    /**
     * 分类分页查询（按下标范围返回命中记录的某一页）
     *
     * @param parentId 父分类ID；{@code null} 查询全部
     * @param keyword 关键字，模糊匹配 category_name；{@code null} / 空则不过滤
     * @param offset 起始行偏移（从 0 开始）
     * @param limit 返回行数上限
     * @return 该页分类列表；无数据时返回空列表（非 null）
     */
    List<ConstantConfigCategory> listPage(Long parentId, String keyword, int offset, int limit);

    /**
     * 按过滤条件统计分类命中总数
     *
     * @param parentId 父分类ID；{@code null} 查询全部
     * @param keyword 关键字，模糊匹配 category_name；{@code null} / 空则不过滤
     * @return 命中总数
     */
    long count(Long parentId, String keyword);

    /**
     * 统计指定分类下的直接子分类数量
     *
     * @param categoryId 分类ID
     * @return 子分类数量
     */
    long countChildren(Long categoryId);
}