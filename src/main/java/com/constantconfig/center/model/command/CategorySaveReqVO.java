package com.constantconfig.center.model.command;

/**
 * 新增 / 更新分类的请求参数（保存入参）
 *
 * <p>承载保存操作入参；{@code categoryId} / {@code path} / {@code level} 由存储层生成或维护。
 * 更新语义：仅按非空字段覆盖；父分类与 path / level 不随之变更。</p>
 */
public class CategorySaveReqVO {

    /** 父分类ID，0 表示根节点 */
    private Long categoryParentId = 0L;

    /** 分类名称（唯一，用于去重） */
    private String categoryName;

    /** 同级排序号 */
    private Integer sort = 0;

    /** 定位用的分类ID（更新时必填） */
    private Long categoryId;

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

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}