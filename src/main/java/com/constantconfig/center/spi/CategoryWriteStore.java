package com.constantconfig.center.spi;

import com.constantconfig.center.model.entity.ConstantConfigCategoryDO;
import com.constantconfig.center.exception.ConstantConfigException;

/**
 * 常量配置分类写入侧存储 SPI（写扩展点）
 *
 * <p>与 {@link CategoryReadStore} 分离，使自定义后端可按需只实现「写」或「读」一侧。
 * {@code path} / {@code level} 的生成逻辑由存储层完成（新增/变更时依据父分类计算并回填），
 * 上层只传名称、父分类与排序号。</p>
 *
 * @see CategoryReadStore
 */
public interface CategoryWriteStore {

    /**
     * 新增分类并自动生成 path / level
     *
     * @param category 分类（提供 {@code categoryName}、{@code categoryParentId}、{@code sort}；id 由存储层生成）
     * @return 新分类ID
     * @throws ConstantConfigException 父分类不存在或名称重复时抛出
     */
    Long create(ConstantConfigCategoryDO category);

    /**
     * 更新分类（按 {@code categoryId} 定位）
     *
     * <p>可更新 {@code categoryName}、{@code sort}；父分类与 {@code path} / {@code level} 不随之变更。</p>
     *
     * @param category 分类（必须携带 {@code categoryId}）
     * @return 目标分类是否存在（不存在返回 {@code false}，由门面转抛异常）
     * @throws ConstantConfigException 名称被其它分类占用时抛出
     */
    boolean update(ConstantConfigCategoryDO category);

    /**
     * 删除分类
     *
     * @param categoryId 分类ID
     * @return 是否删除成功（目标不存在返回 {@code false}，由门面转抛异常）
     */
    boolean delete(Long categoryId);
}