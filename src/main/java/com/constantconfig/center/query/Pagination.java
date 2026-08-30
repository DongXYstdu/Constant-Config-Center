package com.constantconfig.center.query;

/**
 * 分页参数值对象
 *
 * <p>统一负责页码 / 每页大小的规整（clamp）与偏移量计算，避免分页细节散落在门面。
 * 默认每页大小由调用方传入（通常取自 {@code ConstantConfigProperties.defaultPageSize}）。</p>
 *
 * <p>约定：{@code page < 1} 归一为 {@code 1}；{@code size < 1} 时使用默认每页大小；
 * 偏移量 = {@code (page - 1) * size}。</p>
 */
public final class Pagination {

    private final int page;
    private final int size;
    private final int offset;

    private Pagination(int page, int size, int offset) {
        this.page = page;
        this.size = size;
        this.offset = offset;
    }

    /**
     * 构建分页参数，并完成规整与偏移量计算。
     *
     * @param page           请求页码（从 1 开始，小于 1 按 1）
     * @param size           请求每页大小（小于 1 使用默认值）
     * @param defaultPageSize 默认每页大小
     */
    public static Pagination of(int page, int size, int defaultPageSize) {
        int p = page < 1 ? 1 : page;
        int s = size < 1 ? defaultPageSize : size;
        return new Pagination(p, s, (p - 1) * s);
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public int getOffset() {
        return offset;
    }
}