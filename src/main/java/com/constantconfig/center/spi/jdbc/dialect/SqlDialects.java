package com.constantconfig.center.spi.jdbc.dialect;

/**
 * 方言工厂：按数据库产品名选择方言实现，未知/缺省回落为 MySQL 方言。
 */
public final class SqlDialects {

    private SqlDialects() {
    }

    /**
     * 按 {@code DatabaseMetaData#getDatabaseProductName()} 选择方言。
     *
     * @param productName 数据库产品名，可为 {@code null}
     * @return 匹配的方言实现；H2 命中 {@link H2Dialect}，其余归为 {@link MysqlDialect}
     */
    public static SqlDialect detect(String productName) {
        if (productName != null && productName.toUpperCase().contains("H2")) {
            return new H2Dialect();
        }
        return new MysqlDialect();
    }
}