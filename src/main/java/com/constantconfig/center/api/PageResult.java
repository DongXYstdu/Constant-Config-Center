package com.constantconfig.center.api;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用分页结果
 *
 * <p>不依赖任何 Spring Data 类，保持 starter 轻量。</p>
 *
 * @param <T> 分页元素类型
 */
public class PageResult<T> {

    /** 本页数据 */
    private List<T> list = new ArrayList<>();

    /** 总记录数（满足过滤条件的总数） */
    private long total;

    /** 当前页码，从 1 开始 */
    private int page;

    /** 每页大小 */
    private int size;

    public PageResult() {
    }

    public PageResult(List<T> list, long total, int page, int size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return new PageResult<>(list, total, page, size);
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
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