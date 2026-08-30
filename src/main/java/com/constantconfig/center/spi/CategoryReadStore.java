package com.constantconfig.center.spi;

import com.constantconfig.center.model.entity.ConstantConfigCategoryDO;

import java.util.List;

/**
 * 常量配置分类读取侧存储 SPI（读扩展点）
 *
 * <p>与 {@link CategoryWriteStore} 分离，使自定义后端可按需只实现「读」或「写」一侧。
 * 树形结构（{@code children}）属于读视图概念，本层返回平铺列表，树组装由门面层完成。</p>
 *
 * @see CategoryWriteStore
 */
public interface CategoryReadStore {

    /**
     * 按分类ID查询
     *
     * @param categoryId 分类ID
     * @return 分类；不存在时返回 {@code null}
     */
    ConstantConfigCategoryDO get(Long categoryId);

    /**
     * 按分类名称查询（{@code category_name} 全局唯一，用于名称反查与去重）
     *
     * @param categoryName 分类名称
     * @return 分类；不存在时返回 {@code null}
     */
    ConstantConfigCategoryDO getByCategoryName(String categoryName);

    /**
     * 按父ID + 关键字过滤查询分类列表（命中全部，按 path 排序保证父在前）
     *
     * @param parentId 父分类ID；{@code null} 查询全部
     * @param keyword 关键字，模糊匹配 category_name；{@code null} / 空则不过滤
     * @return 分类列表；无数据时返回空列表（非 null）
     */
    List<ConstantConfigCategoryDO> list(Long parentId, String keyword);

    /**
     * 分类分页查询（按下标范围返回命中记录的某一页）
     *
     * @param parentId 父分类ID；{@code null} 查询全部
     * @param keyword 关键字，模糊匹配 category_name；{@code null} / 空则不过滤
     * @param offset 起始行偏移（从 0 开始）
     * @param limit 返回行数上限
     * @return 该页分类列表；无数据时返回空列表（非 null）
     */
    List<ConstantConfigCategoryDO> listPage(Long parentId, String keyword, int offset, int limit);

    /**
     * 按过滤条件统计分类命中总数
     *
     * @param parentId 父分类ID；{@code null} 查询全部
     * @param keyword 关键字，模糊匹配 category_name；{@code null} / 空则不过滤
     * @return 命中总数
     */
    long count(Long parentId, String keyword);

    /**
     * 统计指定分类下的直接子分类数量（用于删除分类前的完整性校验）
     *
     * @param categoryId 分类ID
     * @return 子分类数量
     */
    long countChildren(Long categoryId);
}