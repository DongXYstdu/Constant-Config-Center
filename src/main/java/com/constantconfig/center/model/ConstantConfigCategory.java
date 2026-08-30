package com.constantconfig.center.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 常量配置分类实体（树形）
 *
 * <p>对应 {@code constant_config_category} 表的一行记录。
 * 采用邻接表（{@code category_parent_id}）+ 物化路径（{@code path}）冗余结构。</p>
 */
public class ConstantConfigCategory {

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

    /** 子分类（不落库，仅树查询组装用） */
    private List<ConstantConfigCategory> children = new ArrayList<>();

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

    public List<ConstantConfigCategory> getChildren() {
        return children;
    }

    public void setChildren(List<ConstantConfigCategory> children) {
        this.children = children;
    }
}