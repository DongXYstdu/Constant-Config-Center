package com.constantconfig.center.provider;

import com.constantconfig.center.core.ConstantConfigCenterCategory;
import com.constantconfig.center.core.ConstantConfigCenterCategoryProvider;
import com.constantconfig.center.properties.ConstantConfigCenterProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/**
 * 常量配置分类 JDBC 默认存储实现（内建）
 *
 * <p>基于 {@link JdbcTemplate} 直查 {@code constant_config_category} 表，无缓存。
 * 表名取自 {@link ConstantConfigCenterProperties#getCategoryTable()}，可自定义。</p>
 *
 * <p>新增分类时在本层完成 {@code path} / {@code level} 的生成与回填：先插入拿到自增
 * {@code category_id}，再按 父分类path + "/" + 自身ID 回填 {@code path}。</p>
 */
public class ConstantConfigCenterCategoryJdbcProvider implements ConstantConfigCenterCategoryProvider {

    private final JdbcTemplate jdbcTemplate;
    private final String categoryTable;

    public ConstantConfigCenterCategoryJdbcProvider(JdbcTemplate jdbcTemplate, ConstantConfigCenterProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.categoryTable = properties.getCategoryTable();
    }

    /** 结果集 → 分类模型 */
    private static final RowMapper<ConstantConfigCenterCategory> ROW_MAPPER = (rs, rowNum) -> {
        ConstantConfigCenterCategory category = new ConstantConfigCenterCategory();
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
    public ConstantConfigCenterCategory get(Long categoryId) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + categoryTable
                + " WHERE category_id = ?";
        List<ConstantConfigCenterCategory> categories = jdbcTemplate.query(sql, ROW_MAPPER, categoryId);
        return categories.isEmpty() ? null : categories.get(0);
    }

    @Override
    public ConstantConfigCenterCategory getByCategoryName(String categoryName) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + categoryTable
                + " WHERE category_name = ? LIMIT 1";
        List<ConstantConfigCenterCategory> categories = jdbcTemplate.query(sql, ROW_MAPPER, categoryName);
        return categories.isEmpty() ? null : categories.get(0);
    }

    @Override
    public List<ConstantConfigCenterCategory> list() {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + categoryTable
                + " ORDER BY path, sort";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    @Override
    public Long save(ConstantConfigCenterCategory category) {
        if (category.getCategoryId() != null) {
            throw new IllegalArgumentException("分类更新暂不支持，仅支持新增分类");
        }
        String categoryName = category.getCategoryName();
        if (categoryName == null || categoryName.trim().isEmpty()) {
            throw new IllegalArgumentException("分类名称不能为空");
        }
        if (getByCategoryName(categoryName.trim()) != null) {
            throw new IllegalArgumentException("分类名称已存在：" + categoryName.trim());
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
            ConstantConfigCenterCategory parent = get(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("父分类不存在：categoryId=" + parentId);
            }
            level = parent.getLevel() + 1;
            parentPath = parent.getPath();
        }

        // 先插入（path 留空），拿到自增 category_id 后回填 path
        String insertSql = "INSERT INTO " + categoryTable
                + " (category_parent_id, category_name, path, level, sort) VALUES (?, ?, '', ?, ?)";
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
    }

    @Override
    public boolean delete(Long categoryId) {
        String sql = "DELETE FROM " + categoryTable + " WHERE category_id = ?";
        return jdbcTemplate.update(sql, categoryId) > 0;
    }

    @Override
    public long countChildren(Long categoryId) {
        String sql = "SELECT COUNT(*) FROM " + categoryTable + " WHERE category_parent_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, categoryId);
        return count == null ? 0L : count;
    }
}
