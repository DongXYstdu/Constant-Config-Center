package com.constantconfig.center.model.view;

import java.util.ArrayList;
import java.util.List;

/**
 * 常量配置分类响应视图
 *
 * <p>面向调用方的只读返回对象；{@code children} 用于树形展示（不落库）。</p>
 */
public class CategoryRespVO {

    private Long categoryId;
    private Long categoryParentId;
    private String categoryName;
    private String path;
    private Integer level;
    private Integer sort;
    private List<CategoryRespVO> children = new ArrayList<>();

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

    public List<CategoryRespVO> getChildren() {
        return children;
    }

    public void setChildren(List<CategoryRespVO> children) {
        this.children = children;
    }
}