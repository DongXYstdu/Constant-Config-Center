package com.constantconfig.center.query;

/**
 * 配置分页查询条件（查询入参）
 *
 * <p>{@link com.constantconfig.center.api.ConstantConfigCenter#getConfigPage(ConfigPageReqVO)} 使用。</p>
 */
public class ConfigPageReqVO {

    /** 分类ID过滤；{@code null} 表示不限定分类、查询全部 */
    private Long categoryId;

    /** 关键字；模糊匹配 key 或 config_name；{@code null} / 空串表示不过滤 */
    private String keyword;

    /** 页码，从 1 开始，默认 1 */
    private int page = 1;

    /** 每页大小；0 表示未设置，由门面按 {@code ConstantConfigProperties.defaultPageSize} 兜底 */
    private int size = 0;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
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