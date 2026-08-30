package com.constantconfig.center.spi.jdbc.dialect;

/**
 * SQL 方言抽象：收敛跨库差异，让默认 JDBC 实现不再硬编码某一数据库的语法。
 *
 * <p>B8 已通过列名去保留字消除反引号；剩余方言点收敛为三类：</p>
 * <ul>
 *   <li>{@link #timestampExpression()}：写入 {@code create_time} / {@code update_time} 的时间戳表达式；</li>
 *   <li>{@link #paginateClause()}：分页占位子句，占位参数顺序固定为 (limit, offset)，追加到查询 SQL 后按同序绑定；</li>
 *   <li>{@link #firstRowClause()}：只取一行子句（无占位参数）。</li>
 * </ul>
 *
 * <p>当前随包提供 MySQL（兼容 H2 分页）与 H2 两个默认实现；对接方可在
 * {@code spring.constant-config-center} 下自注册 {@code SqlDialect} Bean 覆盖。</p>
 */
public interface SqlDialect {

    /** 时间戳表达式：兼容 INSERT VALUES 与 UPDATE SET，如 {@code CURRENT_TIMESTAMP} / {@code NOW()}。 */
    String timestampExpression();

    /**
     * 分页占位子句，如 {@code "LIMIT ? OFFSET ?"}。
     *
     * <p>占位参数顺序固定为 (limit, offset)：调用方拼入 SQL 后，需先 bind limit、再 bind offset。
     * 采用 fetch-first 体系的实现（Oracle / SQL Server）参数顺序为 (offset, limit)，接入时注意。</p>
     */
    String paginateClause();

    /** 只取一行子句，无占位参数，如 {@code "LIMIT 1"} / {@code "FETCH FIRST 1 ROWS ONLY"}。 */
    String firstRowClause();
}