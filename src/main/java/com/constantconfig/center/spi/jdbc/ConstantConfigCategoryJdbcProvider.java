package com.constantconfig.center.spi.jdbc;

import com.constantconfig.center.model.entity.ConstantConfigCategoryDO;
import com.constantconfig.center.spi.CategoryReadStore;
import com.constantconfig.center.spi.CategoryWriteStore;
import com.constantconfig.center.spi.jdbc.dialect.SqlDialect;
import com.constantconfig.center.exception.ConstantConfigException;
import com.constantconfig.center.properties.ConstantConfigProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 常量配置分类 JDBC 默认存储实现（内建）
 *
 * <p>基于 {@link JdbcTemplate} 直查 {@code constant_config_category} 表，无缓存。
 * 表名取自 {@link ConstantConfigProperties#getCategoryTable()}，可自定义。</p>
 *
 * <p>新增分类时在本层完成 {@code path} / {@code level} 的生成与回填：先插入拿到自增
 * {@code category_id}，再按 父分类path + "/" + 自身ID 回填 {@code path}。</p>
 */
public class ConstantConfigCategoryJdbcProvider implements CategoryReadStore, CategoryWriteStore {

    private final JdbcTemplate jdbcTemplate;
    private final String categoryTable;
    private final TransactionTemplate transactionTemplate;
    private final SqlDialect dialect;

    public ConstantConfigCategoryJdbcProvider(JdbcTemplate jdbcTemplate,
                                              ConstantConfigProperties properties,
                                              TransactionTemplate transactionTemplate,
                                              SqlDialect dialect) {
        this.jdbcTemplate = jdbcTemplate;
        this.categoryTable = validateTableName(properties.getCategoryTable());
        this.transactionTemplate = transactionTemplate;
        this.dialect = dialect;
    }

    /**
     * 表名白名单校验：仅允许字母/下划线开头、由字母/数字/下划线组成的标识符，
     * 拒绝来自配置的非法表名（防拼入 SQL 造成注入面）。
     */
    private static String validateTableName(String table) {
        if (table == null || !table.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException("非法分类表名（仅允许字母/数字/下划线）：" + table);
        }
        return table;
    }

    /** 结果集 → 分类 DO */
    private static final RowMapper<ConstantConfigCategoryDO> ROW_MAPPER = (rs, rowNum) -> {
        ConstantConfigCategoryDO category = new ConstantConfigCategoryDO();
        category.setCategoryId(rs.getLong("category_id"));
        category.setCategoryParentId(rs.getLong("category_parent_id"));
        category.setCategoryName(rs.getString("category_name"));
        category.setPath(rs.getString("path"));
        category.setLevel(rs.getInt("level"));
        category.setSort(rs.getInt("sort"));
        return category;
    };

    /** 通用查询列 */
    private static final String SELECT_COLUMNS =
            "category_id, category_parent_id, category_name, path, level, sort";

    @Override
    public ConstantConfigCategoryDO get(Long categoryId) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + categoryTable
                + " WHERE category_id = ?";
        List<ConstantConfigCategoryDO> categories = jdbcTemplate.query(sql, ROW_MAPPER, categoryId);
        return categories.isEmpty() ? null : categories.get(0);
    }

    @Override
    public ConstantConfigCategoryDO getByCategoryName(String categoryName) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + categoryTable
                + " WHERE category_name = ? " + dialect.firstRowClause();
        List<ConstantConfigCategoryDO> categories = jdbcTemplate.query(sql, ROW_MAPPER, categoryName);
        return categories.isEmpty() ? null : categories.get(0);
    }

    @Override
    public Long create(ConstantConfigCategoryDO category) {
        if (category.getCategoryId() != null) {
            throw new IllegalArgumentException("分类更新请使用 update，此处仅允许新增分类");
        }
        String categoryName = category.getCategoryName();
        if (categoryName == null || categoryName.trim().isEmpty()) {
            throw new IllegalArgumentException("分类名称不能为空");
        }
        if (getByCategoryName(categoryName.trim()) != null) {
            throw new ConstantConfigException("分类名称已存在：" + categoryName.trim());
        }

        Long parentId = category.getCategoryParentId() == null ? 0L : category.getCategoryParentId();
        int sort = category.getSort() == null ? 0 : category.getSort();

        // 计算层级：根分类 level=1；子分类继承父分类的 path / level
        int level;
        String parentPath;
        if (parentId == 0L) {
            level = 1;
            parentPath = "";
        } else {
            ConstantConfigCategoryDO parent = get(parentId);
            if (parent == null) {
                throw new ConstantConfigException("父分类不存在：categoryId=" + parentId);
            }
            level = parent.getLevel() + 1;
            parentPath = parent.getPath();
        }

        // 先插入（path 留空）拿到自增 category_id，再回填 path；两步在同一事务内，
        // 任一步失败整体回滚，避免留下 path 为空的脏行
        String insertSql = "INSERT INTO " + categoryTable
                + " (category_parent_id, category_name, path, level, sort) VALUES (?, ?, '', ?, ?)";
        return transactionTemplate.execute(status -> {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, parentId);
                ps.setString(2, categoryName.trim());
                ps.setInt(3, level);
                ps.setInt(4, sort);
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("生成分类ID失败");
            }
            Long newId = key.longValue();

            String path = parentPath + "/" + newId;
            String updateSql = "UPDATE " + categoryTable + " SET path = ? WHERE category_id = ?";
            jdbcTemplate.update(updateSql, path, newId);
            return newId;
        });
    }

    @Override
    public boolean update(ConstantConfigCategoryDO category) {
        if (category.getCategoryId() == null) {
            throw new IllegalArgumentException("分类ID不能为空");
        }
        ConstantConfigCategoryDO existing = get(category.getCategoryId());
        if (existing == null) {
            return false;
        }
        String name = category.getCategoryName();
        if (name != null && name.trim().isEmpty()) {
            throw new IllegalArgumentException("分类名称不能为空");
        }
        if (name != null && !name.trim().equals(existing.getCategoryName())) {
            ConstantConfigCategoryDO byName = getByCategoryName(name.trim());
            if (byName != null) {
                throw new ConstantConfigException("分类名称已存在：" + name.trim());
            }
        }
        String finalName = name != null ? name.trim() : existing.getCategoryName();
        Integer sort = category.getSort() != null ? category.getSort() : existing.getSort();

        String sql = "UPDATE " + categoryTable + " SET category_name = ?, sort = ? WHERE category_id = ?";
        jdbcTemplate.update(sql, finalName, sort, existing.getCategoryId());
        return true;
    }

    @Override
    public boolean delete(Long categoryId) {
        String sql = "DELETE FROM " + categoryTable + " WHERE category_id = ?";
        try {
            return jdbcTemplate.update(sql, categoryId) > 0;
        } catch (DataIntegrityViolationException e) {
            // 依赖外键 fk_ccc_category_id(ON DELETE RESTRICT) 在 DELETE 时原子兜底：
            // 分类下仍存在配置时，由数据库拦截，而非应用层「先查再删」的非原子预检
            throw new ConstantConfigException(
                    "分类下仍存在配置，无法删除：categoryId=" + categoryId, e);
        }
    }

    @Override
    public List<ConstantConfigCategoryDO> list(Long parentId, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT " + SELECT_COLUMNS + " FROM " + categoryTable + " WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        buildFilter(sql, args, parentId, keyword);
        sql.append(" ORDER BY path, sort");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    @Override
    public List<ConstantConfigCategoryDO> listPage(Long parentId, String keyword, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + SELECT_COLUMNS + " FROM " + categoryTable + " WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        buildFilter(sql, args, parentId, keyword);
        sql.append(" ORDER BY path, sort ").append(dialect.paginateClause());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    @Override
    public long count(Long parentId, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM " + categoryTable + " WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        buildFilter(sql, args, parentId, keyword);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    @Override
    public long countChildren(Long categoryId) {
        String sql = "SELECT COUNT(*) FROM " + categoryTable + " WHERE category_parent_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, categoryId);
        return count == null ? 0L : count;
    }

    /** 追加按父分类 + 关键字的过滤条件与参数 */
    private void buildFilter(StringBuilder sql, List<Object> args, Long parentId, String keyword) {
        if (parentId != null) {
            sql.append(" AND category_parent_id = ?");
            args.add(parentId);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND category_name LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
    }
}