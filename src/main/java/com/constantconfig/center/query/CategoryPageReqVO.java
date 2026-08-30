package com.constantconfig.center.query;

/**
 * 分类分页查询条件（查询入参）
 *
 * <p>{@link com.constantconfig.center.api.ConstantConfigCenter#getCategoryPage(CategoryPageReqVO)} 使用。</p>
 */
public class CategoryPageReqVO {

    /** 父分类ID过滤；{@code null} 表示不限父级、查询全部 */
    private Long parentId;

    /** 关键字；模糊匹配 category_name；{@code null} / 空串表示不过滤 */
    private String keyword;

    /** 页码，从 1 开始，默认 1 */
    private int page = 1;

    /** 每页大小；0 表示未设置，由门面按 {@code ConstantConfigProperties.defaultPageSize} 兜底 */
    private int size = 0;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}