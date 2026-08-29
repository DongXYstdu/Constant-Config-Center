package com.constantconfig.center.provider;

import com.constantconfig.center.core.ConstantConfigCenterConflictException;
import com.constantconfig.center.core.ConstantConfigCenterItem;
import com.constantconfig.center.core.ConstantConfigCenterProvider;
import com.constantconfig.center.core.ConstantConfigCenterValueType;
import com.constantconfig.center.properties.ConstantConfigCenterProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.util.List;

/**
 * 常量配置中心 JDBC 默认存储实现（内建）
 *
 * <p>基于 {@link JdbcTemplate} 直查 {@code constant_config_center} 表，无缓存。
 * 表名取自 {@link ConstantConfigCenterProperties#getTable()}，可自定义。</p>
 *
 * <p>注意：{@code key} 是 MySQL 保留字，所有 SQL 中列名统一加反引号 {@code `key`} 包裹。</p>
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
    public ConstantConfigCenterItem get(Long categoryId, String key) {
        // key 全局唯一（uk_key），直接按 key 定位；categoryId 仅作兼容保留，不参与过滤
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
    public List<ConstantConfigCenterItem> list(Long categoryId) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + table
                + " WHERE category_id = ? ORDER BY config_name";
        return jdbcTemplate.query(sql, ROW_MAPPER, categoryId);
    }

    @Override
    public void save(ConstantConfigCenterItem item) {
        // 纯 INSERT（不做静默覆盖）：config_name（uk_config_key）或 key（uk_key）任一全局唯一键
        // 冲突时抛 DuplicateKeyException，此处反查已存在行并转抛 ConflictException（携带主键 id）。
        String sql = "INSERT INTO " + table
                + " (category_id, config_name, `key`, `value`, value_type, version, remark, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, ?, 0, ?, NOW(), NOW())";
        try {
            jdbcTemplate.update(sql,
                    item.getCategoryId(),
                    item.getConfigName(),
                    item.getKey(),
                    item.getValue(),
                    item.getValueType().name(),
                    item.getRemark());
        } catch (DuplicateKeyException e) {
            throw conflictException(item);
        }
    }

    /**
     * 反查冲突来源并构造 {@link ConstantConfigCenterConflictException}
     *
     * <p>优先判定 {@code config_name} 冲突；若名称未命中再判定 {@code key} 冲突。
     * 极端并发下（冲突行已被删除）反查不到时，退化为通用 {@link IllegalArgumentException}。</p>
     */
    private ConstantConfigCenterConflictException conflictException(ConstantConfigCenterItem item) {
        ConstantConfigCenterItem byName = getByConfigName(item.getConfigName());
        if (byName != null) {
            return new ConstantConfigCenterConflictException(
                    byName.getId(), "config_name", item.getConfigName());
        }
        ConstantConfigCenterItem byKey = get(0L, item.getKey());
        if (byKey != null) {
            return new ConstantConfigCenterConflictException(
                    byKey.getId(), "key", item.getKey());
        }
        throw new IllegalArgumentException(
                "常量配置保存失败：唯一键冲突但无法定位已存在记录（config_name=" + item.getConfigName()
                        + ", key=" + item.getKey() + "）");
    }

    @Override
    public boolean delete(Long categoryId, String key) {
        // key 全局唯一（uk_key），直接按 key 删除；categoryId 仅作兼容保留，不参与过滤
        String sql = "DELETE FROM " + table + " WHERE `key` = ?";
        return jdbcTemplate.update(sql, key) > 0;
    }
}
