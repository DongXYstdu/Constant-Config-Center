package com.constantconfig.center.spi.jdbc.dialect;

/**
 * MySQL 方言（默认）。
 *
 * <p>分页/取单行采用 {@code LIMIT} 体系，时间戳用 MySQL 的 {@code NOW()}。</p>
 */
public class MysqlDialect implements SqlDialect {

    @Override
    public String timestampExpression() {
        return "NOW()";
    }

    @Override
    public String paginateClause() {
        return "LIMIT ? OFFSET ?";
    }

    @Override
    public String firstRowClause() {
        return "LIMIT 1";
    }
}