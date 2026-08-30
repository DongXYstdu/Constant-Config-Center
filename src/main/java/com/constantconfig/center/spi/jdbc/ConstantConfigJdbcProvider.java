package com.constantconfig.center.spi.jdbc;

import com.constantconfig.center.model.entity.ConstantConfigDO;
import com.constantconfig.center.model.ConstantConfigValueType;
import com.constantconfig.center.spi.ConfigReadStore;
import com.constantconfig.center.spi.ConfigWriteStore;
import com.constantconfig.center.spi.jdbc.dialect.SqlDialect;
import com.constantconfig.center.exception.ConstantConfigConflictException;
import com.constantconfig.center.exception.ConstantConfigVersionMismatchException;
import com.constantconfig.center.properties.ConstantConfigProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * 常量配置 JDBC 默认存储实现（内建）
 *
 * <p>基于 {@link JdbcTemplate} 直查 {@code constant_config_center} 表，无缓存。
 * 表名取自 {@link ConstantConfigProperties#getTable()}，可自定义。</p>
 *
 * <p>B8：{@code key} / {@code value} 已改名为 {@code config_key} / {@code config_value}，
 * 摆脱 MySQL 保留字，SQL 不再需要反引号包裹标识符，MySQL / H2 双库均可直接使用。</p>
 *
 * <p>{@code config_key} 全局唯一，本实现以其作为读写删的定位键，不再使用 categoryId 定位。</p>
 */
public class ConstantConfigJdbcProvider implements ConfigReadStore, ConfigWriteStore {

    private final JdbcTemplate jdbcTemplate;
    private final String table;
    private final long defaultVersion;
    private final SqlDialect dialect;

    public ConstantConfigJdbcProvider(JdbcTemplate jdbcTemplate,
                                      ConstantConfigProperties properties,
                                      SqlDialect dialect) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = validateTableName(properties.getTable());
        this.defaultVersion = properties.getDefaultVersion();
        this.dialect = dialect;
    }

    /**
     * 表名白名单校验：仅允许字母/下划线开头、由字母/数字/下划线组成的标识符，
     * 拒绝来自配置的非法表名（防拼入 SQL 造成注入面）。
     */
    private static String validateTableName(String table) {
        if (table == null || !table.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException("非法配置表名（仅允许字母/数字/下划线）：" + table);
        }
        return table;
    }

    /** 结果集 → 配置条目 DO */
    private static final RowMapper<ConstantConfigDO> ROW_MAPPER = (rs, rowNum) -> {
        ConstantConfigDO item = new ConstantConfigDO();
        item.setId(rs.getLong("id"));
        item.setCategoryId(rs.getLong("category_id"));
        item.setConfigName(rs.getString("config_name"));
        item.setKey(rs.getString("config_key"));
        item.setValue(rs.getString("config_value"));
        item.setValueType(ConstantConfigValueType.ofStrict(rs.getString("value_type")));
        item.setVersion(rs.getLong("version"));
        item.setRemark(rs.getString("remark"));

        Timestamp createTime = rs.getTimestamp("create_time");
        if (createTime != null) {
            item.setCreateTime(createTime.toLocalDateTime());
        }
        Timestamp updateTime = rs.getTimestamp("update_time");
        if (updateTime != null) {
            item.setUpdateTime(updateTime.toLocalDateTime());
        }
        return item;
    };

    /** 通用查询列 */
    private static final String SELECT_COLUMNS =
            "id, category_id, config_name, config_key, config_value, value_type, version, remark, create_time, update_time";

    @Override
    public ConstantConfigDO get(String key) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + table
                + " WHERE config_key = ? " + dialect.firstRowClause();
        List<ConstantConfigDO> items = jdbcTemplate.query(sql, ROW_MAPPER, key);
        return items.isEmpty() ? null : items.get(0);
    }

    @Override
    public ConstantConfigDO getByConfigName(String configName) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + table
                + " WHERE config_name = ? " + dialect.firstRowClause();
        List<ConstantConfigDO> items = jdbcTemplate.query(sql, ROW_MAPPER, configName);
        return items.isEmpty() ? null : items.get(0);
    }

    @Override
    public Long create(ConstantConfigDO item) {
        // 纯 INSERT（不做静默覆盖）：config_name（uk_config_name）或 config_key（uk_config_key）任一全局唯一键
        // 冲突时抛 DuplicateKeyException，此处反查已存在行并转抛 ConflictException（携带主键 id）。
        String insertSql = "INSERT INTO " + table
                + " (category_id, config_name, config_key, config_value, value_type, version, remark, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, " + dialect.timestampExpression() + ", "
                + dialect.timestampExpression() + ")";
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, item.getCategoryId());
                ps.setString(2, item.getConfigName());
                ps.setString(3, item.getKey());
                ps.setString(4, item.getValue());
                ps.setString(5, item.getValueType().name());
                ps.setLong(6, defaultVersion);
                ps.setString(7, item.getRemark());
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("生成配置ID失败");
            }
            return key.longValue();
        } catch (DuplicateKeyException e) {
            throw conflictException(item);
        }
    }

    @Override
    public boolean update(ConstantConfigDO item) {
        // 契约：item 为门面已合并好的完整快照（key 定位 + 期望 version CAS），本层不做「null 补丁合并」。
        // config_name 冲突由 uk_config_name 唯一键拦截，复现为 ConflictException；0 受影响行区分
        // 「定位键已不存在」与「版本被并发修改」，分别返回 false 或抛 VersionMismatch。
        long expectedVersion = item.getVersion() != null ? item.getVersion() : 0L;
        String sql = "UPDATE " + table
                + " SET category_id = ?, config_name = ?, config_value = ?, value_type = ?, remark = ?, "
                + "version = version + 1, update_time = " + dialect.timestampExpression()
                + " WHERE config_key = ? AND version = ?";
        int affected;
        try {
            affected = jdbcTemplate.update(
                    sql, item.getCategoryId(), item.getConfigName(), item.getValue(),
                    item.getValueType().name(), item.getRemark(), item.getKey(), expectedVersion);
        } catch (DuplicateKeyException e) {
            // 新 config_name 与其它记录冲突（uk_config_name）；反查冲突行携带其主键 id
            ConstantConfigDO byName = getByConfigName(item.getConfigName());
            Long existingId = byName != null ? byName.getId() : null;
            throw new ConstantConfigConflictException(existingId, "config_name", item.getConfigName());
        }
        if (affected == 0) {
            ConstantConfigDO current = get(item.getKey());
            if (current == null) {
                return false; // 定位键已在「读快照 → 更新」窗口内被删除
            }
            throw new ConstantConfigVersionMismatchException(item.getKey(), expectedVersion, current.getVersion());
        }
        return true;
    }

    @Override
    public boolean delete(String key) {
        String sql = "DELETE FROM " + table + " WHERE config_key = ?";
        return jdbcTemplate.update(sql, key) > 0;
    }

    @Override
    public List<ConstantConfigDO> list(Long categoryId, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT " + SELECT_COLUMNS + " FROM " + table + " WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        buildFilter(sql, args, categoryId, keyword);
        sql.append(" ORDER BY config_name");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    @Override
    public List<ConstantConfigDO> listPage(Long categoryId, String keyword, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + SELECT_COLUMNS + " FROM " + table + " WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        buildFilter(sql, args, categoryId, keyword);
        sql.append(" ORDER BY config_name ").append(dialect.paginateClause());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    @Override
    public long count(Long categoryId, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM " + table + " WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        buildFilter(sql, args, categoryId, keyword);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    /** 追加按分类 + 关键字的过滤条件与参数 */
    private void buildFilter(StringBuilder sql, List<Object> args, Long categoryId, String keyword) {
        if (categoryId != null) {
            sql.append(" AND category_id = ?");
            args.add(categoryId);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (config_name LIKE ? OR config_key LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
    }

    /**
     * 反查冲突来源并构造 {@link ConstantConfigConflictException}
     *
     * <p>优先判定 {@code config_name} 冲突；若名称未命中再判定 {@code key} 冲突。
     * 极端并发下（冲突行已被删除）反查不到时，退化为回抛原唯一键冲突异常。</p>
     */
    private ConstantConfigConflictException conflictException(ConstantConfigDO item) {
        ConstantConfigDO byName = getByConfigName(item.getConfigName());
        if (byName != null) {
            return new ConstantConfigConflictException(
                    byName.getId(), "config_name", item.getConfigName());
        }
        ConstantConfigDO byKey = get(item.getKey());
        if (byKey != null) {
            return new ConstantConfigConflictException(
                    byKey.getId(), "key", item.getKey());
        }
        return new ConstantConfigConflictException(null, "key", item.getKey());
    }
}