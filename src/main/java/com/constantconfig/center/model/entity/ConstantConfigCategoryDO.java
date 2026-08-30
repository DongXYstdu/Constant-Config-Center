package com.constantconfig.center.model.entity;

/**
 * 常量配置分类存储数据载体（DO/Entity）
 *
 * <p>对应 {@code constant_config_category} 表的一行记录，仅在存储 SPI 与实现间传递。
 * 采用邻接表（{@code categoryParentId}）+ 物化路径（{@code path}）冗余结构；
 * 树的 {@code children} 由读视图 {@code CategoryRespVO} 承载，不在 DO 上。</p>
 */
public class ConstantConfigCategoryDO {

    /** 分类ID */
    private Long categoryId;

    /** 父分类ID，0 表示根节点 */
    private Long categoryParentId = 0L;

    /** 分类名称（唯一，用于去重） */
    private String categoryName;

    /** 层级路径，如 /1/2/3，根节点为 /1 */
    private String path;

    /** 层级深度，根为 1 */
    private Integer level = 1;

    /** 同级排序号 */
    private Integer sort = 0;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getCategoryParentId() {
        return categoryParentId;
    }

    public void setCategoryParentId(Long categoryParentId) {
        this.categoryParentId = categoryParentId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    /** 逐字段拷贝（供缓存写入前作防御性快照，避免共享可变对象被外部篡改污染） */
    public ConstantConfigCategoryDO copy() {
        ConstantConfigCategoryDO copy = new ConstantConfigCategoryDO();
        copy.categoryId = this.categoryId;
        copy.categoryParentId = this.categoryParentId;
        copy.categoryName = this.categoryName;
        copy.path = this.path;
        copy.level = this.level;
        copy.sort = this.sort;
        return copy;
    }
}