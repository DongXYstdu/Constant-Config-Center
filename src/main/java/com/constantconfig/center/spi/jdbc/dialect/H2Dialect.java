package com.constantconfig.center.spi.jdbc.dialect;

/**
 * H2 方言（测试/嵌入式默认）。
 *
 * <p>分页/取单行采用与 MySQL 一致的 {@code LIMIT} 体系；时间戳改用 ANSI 标准
 * {@code CURRENT_TIMESTAMP}（H2 原生支持），避免依赖 {@code NOW()}。</p>
 */
public class H2Dialect implements SqlDialect {

    @Override
    public String timestampExpression() {
        return "CURRENT_TIMESTAMP";
    }

    @Override
    public String paginateClause() {
        return "LIMIT ? OFFSET ?";
    }

    @Override
    public String firstRowClause() {
        return "LIMIT 1";
    }

    @Override
    public boolean supports(String productName) {
        return productName != null && productName.toUpperCase().contains("H2");
    }
}