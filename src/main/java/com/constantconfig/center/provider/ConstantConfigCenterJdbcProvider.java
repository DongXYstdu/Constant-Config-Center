package com.constantconfig.center.provider;

import com.constantconfig.center.core.ConstantConfigCenterConflictException;
import com.constantconfig.center.core.ConstantConfigCenterItem;
import com.constantconfig.center.core.ConstantConfigCenterProvider;
import com.constantconfig.center.core.ConstantConfigCenterValueType;
import com.constantconfig.center.properties.ConstantConfigCenterProperties;
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
 * 常量配置中心 JDBC 默认存储实现（内建）
 *
 * <p>基于 {@link JdbcTemplate} 直查 {@code constant_config_center} 表，无缓存。
 * 表名取自 {@link ConstantConfigCenterProperties#getTable()}，可自定义。</p>
 *
 * <p>注意：{@code key} 是 MySQL 保留字，所有 SQL 中列名统一加反引号 {@code `key`} 包裹；
 * {@code value} 是 H2 保留字（VALUE），同样反引号 {@code `value`} 包裹，以保证 MySQL / H2 双库可跑。</p>
 *
 * <p>{@code key} 全局唯一，本实现以 {@code key} 作为读写删的定位键，不再使用 categoryId 定位。</p>
 */
public class ConstantConfigCenterJdbcProvider implements ConstantConfigCenterProvider {

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public ConstantConfigCenterJdbcProvider(JdbcTemplate jdbcTemplate, ConstantConfigCenterProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = properties.getTable();
    }

    /** 结果集 → 配置条目模型 */
    private static final RowMapper<ConstantConfigCenterItem> ROW_MAPPER = (rs, rowNum) -> {
        ConstantConfigCenterItem item = new ConstantConfigCenterItem();
        item.setId(rs.getLong("id"));
        item.setCategoryId(rs.getLong("category_id"));
        item.setConfigName(rs.getString("config_name"));
        item.setKey(rs.getString("key"));
        item.setValue(rs.getString("value"));
        item.setValueType(ConstantConfigCenterValueType.of(rs.getString("value_type")));
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
            "id, category_id, config_name, `key`, `value`, value_type, version, remark, create_time, update_time";

    @Override
    public ConstantConfigCenterItem get(String key) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + table
                + " WHERE `key` = ? LIMIT 1";
        List<ConstantConfigCenterItem> items = jdbcTemplate.query(sql, ROW_MAPPER, key);
        return items.isEmpty() ? null : items.get(0);
    }

    @Override
    public ConstantConfigCenterItem getByConfigName(String configName) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + table
                + " WHERE config_name = ? LIMIT 1";
        List<ConstantConfigCenterItem> items = jdbcTemplate.query(sql, ROW_MAPPER, configName);
        return items.isEmpty() ? null : items.get(0);
    }

    @Override
    public Long create(ConstantConfigCenterItem item) {
        // 纯 INSERT（不做静默覆盖）：config_name（uk_config_key）或 key（uk_key）任一全局唯一键
        // 冲突时抛 DuplicateKeyException，此处反查已存在行并转抛 ConflictException（携带主键 id）。
        String insertSql = "INSERT INTO " + table
                + " (category_id, config_name, `key`, `value`, value_type, version, remark, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, ?, 0, ?, NOW(), NOW())";
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, item.getCategoryId());
                ps.setString(2, item.getConfigName());
                ps.setString(3, item.getKey());
                ps.setString(4, item.getValue());
                ps.setString(5, item.getValueType().name());
                ps.setString(6, item.getRemark());
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
    public boolean update(ConstantConfigCenterItem item) {
        ConstantConfigCenterItem existing = get(item.getKey());
        if (existing == null) {
            return false;
        }
        // 合并补丁语义：入参为 null 的字段保留原值；key 为定位键，不可变
        Long categoryId = item.getCategoryId() != null ? item.getCategoryId() : existing.getCategoryId();
        String configName = item.getConfigName() != null ? item.getConfigName() : existing.getConfigName();
        String value = item.getValue() != null ? item.getValue() : existing.getValue();
        ConstantConfigCenterValueType valueType =
                item.getValueType() != null ? item.getValueType() : existing.getValueType();
        String remark = item.getRemark() != null ? item.getRemark() : existing.getRemark();

        // configName 若变化，校验其全局唯一（排除自身）
        if (!configName.equals(existing.getConfigName())) {
            ConstantConfigCenterItem byName = getByConfigName(configName);
            if (byName != null) {
                throw new ConstantConfigCenterConflictException(byName.getId(), "config_name", configName);
            }
        }

        String sql = "UPDATE " + table
                + " SET category_id = ?, config_name = ?, `value` = ?, value_type = ?, remark = ?, "
                + "version = version + 1, update_time = NOW() WHERE `key` = ?";
        jdbcTemplate.update(sql, categoryId, configName, value, valueType.name(), remark, item.getKey());
        return true;
    }

    @Override
    public boolean delete(String key) {
        String sql = "DELETE FROM " + table + " WHERE `key` = ?";
        return jdbcTemplate.update(sql, key) > 0;
    }

    @Override
    public List<ConstantConfigCenterItem> list(Long categoryId, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT " + SELECT_COLUMNS + " FROM " + table + " WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        buildFilter(sql, args, categoryId, keyword);
        sql.append(" ORDER BY config_name");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    @Override
    public List<ConstantConfigCenterItem> listPage(Long categoryId, String keyword, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + SELECT_COLUMNS + " FROM " + table + " WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        buildFilter(sql, args, categoryId, keyword);
        sql.append(" ORDER BY config_name LIMIT ? OFFSET ?");
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
            sql.append(" AND (config_name LIKE ? OR `key` LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
    }

    /**
     * 反查冲突来源并构造 {@link ConstantConfigCenterConflictException}
     *
     * <p>优先判定 {@code config_name} 冲突；若名称未命中再判定 {@code key} 冲突。
     * 极端并发下（冲突行已被删除）反查不到时，退化为回抛原唯一键冲突异常。</p>
     */
    private ConstantConfigCenterConflictException conflictException(ConstantConfigCenterItem item) {
        ConstantConfigCenterItem byName = getByConfigName(item.getConfigName());
        if (byName != null) {
            return new ConstantConfigCenterConflictException(
                    byName.getId(), "config_name", item.getConfigName());
        }
        ConstantConfigCenterItem byKey = get(item.getKey());
        if (byKey != null) {
            return new ConstantConfigCenterConflictException(
                    byKey.getId(), "key", item.getKey());
        }
        return new ConstantConfigCenterConflictException(null, "key", item.getKey());
    }
}