package com.constantconfig.center;

import com.constantconfig.center.api.ConstantConfigCenter;
import com.constantconfig.center.api.CategoryPageQuery;
import com.constantconfig.center.api.ConfigPageQuery;
import com.constantconfig.center.api.PageResult;
import com.constantconfig.center.core.ConstantConfigCenterCategory;
import com.constantconfig.center.core.ConstantConfigCenterConflictException;
import com.constantconfig.center.core.ConstantConfigCenterException;
import com.constantconfig.center.core.ConstantConfigCenterItem;
import com.constantconfig.center.core.ConstantConfigCenterNotFoundException;
import com.constantconfig.center.core.ConstantConfigCenterSerializationException;
import com.constantconfig.center.core.ConstantConfigCenterValueType;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 常量配置中心集成测试（H2 内存库，MySQL 兼容模式）
 *
 * <p>覆盖新门面 API：STRING / LIST / MAP 读写、唯一键冲突（ConflictException + existingId）、
 * 更新/删除目标不存在（NotFoundException）、列表与分页、分类 CRUD 与树。</p>
 */
@SpringBootTest(classes = ConstantConfigCenterTestApplication.class)
class ConstantConfigCenterIntegrationTest {

    @Autowired
    private ConstantConfigCenter ccc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        // 每个用例前清空配置表与分类表（保留 category_id=1 默认分类），避免相互污染
        jdbcTemplate.update("DELETE FROM constant_config_center");
        jdbcTemplate.update("DELETE FROM constant_config_category WHERE category_id != 1");
    }

    // ────────────────────── 读取 / 写入（STRING） ──────────────────────

    @Test
    void createAndGetString() {
        Long id = ccc.createConfig(item("比功率有效区间", "iot.power.range", "[1,50]"));
        assertNotNull(id);
        assertEquals("[1,50]", ccc.getConfig("iot.power.range"));
        assertEquals("[1,50]", ccc.getConfig("iot.power.range", "fallback"));
        // 名称反查 key
        assertEquals("iot.power.range", ccc.getKeyByConfigName("比功率有效区间"));
    }

    @Test
    void getConfigReturnsDefaultWhenMissing() {
        assertNull(ccc.getConfig("not.exists"));
        assertEquals("fallback", ccc.getConfig("not.exists", "fallback"));
        assertNull(ccc.getKeyByConfigName("不存在的名称"));
    }

    // ────────────────────── 唯一键冲突规则（核心） ──────────────────────

    @Test
    void configNameConflictThrowsConflictExceptionWithExistingId() {
        ccc.createConfig(item("配置A", "key.a", "v1"));

        ConstantConfigCenterConflictException ex = assertThrows(
                ConstantConfigCenterConflictException.class,
                () -> ccc.createConfig(item("配置A", "key.b", "v2")));

        assertEquals("config_name", ex.getConflictField());
        assertEquals("配置A", ex.getConflictValue());
        Long existingId = jdbcTemplate.queryForObject(
                "SELECT id FROM constant_config_center WHERE config_name = ?", Long.class, "配置A");
        assertNotNull(existingId);
        assertEquals(existingId, ex.getExistingId());
    }

    @Test
    void keyConflictThrowsConflictExceptionWithExistingId() {
        ccc.createConfig(item("配置A", "key.x", "v1"));

        ConstantConfigCenterConflictException ex = assertThrows(
                ConstantConfigCenterConflictException.class,
                () -> ccc.createConfig(item("配置B", "key.x", "v2")));

        assertEquals("key", ex.getConflictField());
        assertEquals("key.x", ex.getConflictValue());
        Long existingId = jdbcTemplate.queryForObject(
                "SELECT id FROM constant_config_center WHERE `key` = ?", Long.class, "key.x");
        assertNotNull(existingId);
        assertEquals(existingId, ex.getExistingId());
    }

    @Test
    void conflictDoesNotOverwriteExistingRow() {
        ccc.createConfig(item("配置A", "key.a", "v1"));
        assertThrows(ConstantConfigCenterConflictException.class,
                () -> ccc.createConfig(item("配置A", "key.b", "v2")));
        assertEquals("v1", ccc.getConfig("key.a"));
        assertNull(ccc.getConfig("key.b"));
    }

    // ────────────────────── LIST / MAP 序列化 ──────────────────────

    @Test
    void listReadWrite() {
        ConstantConfigCenterItem listItem = item("允许列表", "iot.allow.list", null);
        listItem.setValueObject(Arrays.asList("a", "b", "c"));
        listItem.setValueType(ConstantConfigCenterValueType.LIST);
        ccc.createConfig(listItem);

        List<String> list = ccc.getConfig("iot.allow.list", new TypeReference<List<String>>() {});
        assertEquals(Arrays.asList("a", "b", "c"), list);
    }

    @Test
    void mapReadWrite() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("scale", 0.1);
        map.put("enabled", true);
        ConstantConfigCenterItem mapItem = item("控制参数", "iot.param", null);
        mapItem.setValueObject(map);
        mapItem.setValueType(ConstantConfigCenterValueType.MAP);
        ccc.createConfig(mapItem);

        Map<String, Object> result = ccc.getConfig("iot.param", new TypeReference<Map<String, Object>>() {});
        assertEquals(0.1, ((Number) result.get("scale")).doubleValue());
        assertEquals(true, result.get("enabled"));
    }

    @Test
    void invalidJsonForTypedReadThrowsSerializationException() {
        ccc.createConfig(item("坏值", "iot.bad", "not-json"));
        // 字符串原样返回
        assertEquals("not-json", ccc.getConfig("iot.bad"));
        // 按 List 反序列化失败 → SerializationException
        assertThrows(ConstantConfigCenterSerializationException.class,
                () -> ccc.getConfig("iot.bad", new TypeReference<List<?>>() {}));
    }

    // ────────────────────── 更新 / 删除 ──────────────────────

    @Test
    void updateConfigModifiesValueAndIncrementsVersion() {
        ccc.createConfig(item("目标压力", "iot.pressure.target", "0.1"));
        Long versionBefore = jdbcTemplate.queryForObject(
                "SELECT version FROM constant_config_center WHERE `key` = ?", Long.class, "iot.pressure.target");

        ConstantConfigCenterItem upd = item("目标压力2", "iot.pressure.target", "0.2");
        upd.setRemark("调参");
        ccc.updateConfig(upd);

        assertEquals("0.2", ccc.getConfig("iot.pressure.target"));
        Long versionAfter = jdbcTemplate.queryForObject(
                "SELECT version FROM constant_config_center WHERE `key` = ?", Long.class, "iot.pressure.target");
        assertEquals(versionBefore + 1, versionAfter);
    }

    @Test
    void updateConfigNameConflictThrows() {
        ccc.createConfig(item("A", "k1", "v1"));
        ccc.createConfig(item("B", "k2", "v2"));
        // 把 k1 改成已占用的名称 B
        ConstantConfigCenterItem upd = new ConstantConfigCenterItem();
        upd.setKey("k1");
        upd.setConfigName("B");
        assertThrows(ConstantConfigCenterConflictException.class, () -> ccc.updateConfig(upd));
    }

    @Test
    void updateConfigMissingThrowsNotFound() {
        ConstantConfigCenterItem upd = item("不存在", "no.such.key", "x");
        assertThrows(ConstantConfigCenterNotFoundException.class, () -> ccc.updateConfig(upd));
    }

    @Test
    void deleteConfig() {
        ccc.createConfig(item("c1", "k1", "v1"));
        ccc.deleteConfig("k1");
        assertNull(ccc.getConfig("k1"));
        // 重复删除不存在 → NotFoundException
        assertThrows(ConstantConfigCenterNotFoundException.class, () -> ccc.deleteConfig("k1"));
    }

    // ────────────────────── 列表 / 分页 ──────────────────────

    @Test
    void listConfigsFilteredByCategoryAndKeyword() {
        ccc.createConfig(item("水位阈值", "iot.water.high", "80"));
        ccc.createConfig(item("压力上限", "iot.pressure.max", "1.5"));
        ccc.createConfig(item("温度下限", "iot.temp.min", "-20"));

        // 按关键字（匹配 key）
        List<ConstantConfigCenterItem> list = ccc.getConfigList(null, "press");
        assertEquals(1, list.size());
        assertEquals("压力上限", list.get(0).getConfigName());

        // 按关键字（匹配 config_name）
        assertEquals(1, ccc.getConfigList(null, "水位").size());

        // 全量
        assertEquals(3, ccc.getConfigList(null, null).size());
    }

    @Test
    void pageConfigs() {
        ccc.createConfig(item("k1", "c.k1", "v1"));
        ccc.createConfig(item("k2", "c.k2", "v2"));
        ccc.createConfig(item("k3", "c.k3", "v3"));

        ConfigPageQuery q1 = new ConfigPageQuery();
        q1.setPage(1);
        q1.setSize(2);
        PageResult<ConstantConfigCenterItem> p1 = ccc.getConfigPage(q1);
        assertEquals(3, p1.getTotal());
        assertEquals(2, p1.getList().size());
        assertEquals(1, p1.getPage());
        assertEquals(2, p1.getSize());

        ConfigPageQuery q2 = new ConfigPageQuery();
        q2.setPage(2);
        q2.setSize(2);
        assertEquals(1, ccc.getConfigPage(q2).getList().size());
    }

    // ────────────────────── 分类管理（CRUD + 树） ──────────────────────

    private ConstantConfigCenterCategory category(String name, Long parentId, Integer sort) {
        ConstantConfigCenterCategory category = new ConstantConfigCenterCategory();
        category.setCategoryName(name);
        category.setCategoryParentId(parentId);
        category.setSort(sort);
        return category;
    }

    @Test
    void createCategoryGeneratesPathAndLevel() {
        Long childId = ccc.createCategory(category("传感器", 1L, 0));

        List<ConstantConfigCenterCategory> roots = ccc.listCategoryTree();
        assertEquals(1, roots.size());
        List<ConstantConfigCenterCategory> children = roots.get(0).getChildren();
        assertEquals(1, children.size());
        assertEquals("传感器", children.get(0).getCategoryName());
        assertEquals("/1/" + childId, children.get(0).getPath());
        assertEquals(2, children.get(0).getLevel());
    }

    @Test
    void updateCategoryRenames() {
        Long id = ccc.createCategory(category("传感器", 1L, 0));
        ConstantConfigCenterCategory upd = new ConstantConfigCenterCategory();
        upd.setCategoryId(id);
        upd.setCategoryName("功率计");
        ccc.updateCategory(upd);

        assertEquals("功率计", ccc.getCategoryList(1L, null).get(0).getCategoryName());
    }

    @Test
    void categoryNamesAreUniqueAcrossSiblings() {
        ccc.createCategory(category("重复分类", 1L, 0));
        assertThrows(ConstantConfigCenterException.class,
                () -> ccc.createCategory(category("重复分类", 1L, 0)));
    }

    @Test
    void deleteCategoryWithChildrenThrows() {
        ccc.createCategory(category("子分类", 1L, 0));
        assertThrows(ConstantConfigCenterException.class,
                () -> ccc.deleteCategory(ConstantConfigCenter.DEFAULT_CATEGORY_ID));
    }

    @Test
    void deleteLeafCategorySucceeds() {
        Long childId = ccc.createCategory(category("叶子分类", 1L, 0));
        ccc.deleteCategory(childId);
        assertTrue(ccc.listCategoryTree().get(0).getChildren().isEmpty());
        assertThrows(ConstantConfigCenterNotFoundException.class, () -> ccc.deleteCategory(childId));
    }

    @Test
    void pageCategories() {
        ccc.createCategory(category("传感器", 1L, 0));
        ccc.createCategory(category("仪表", 1L, 0));

        CategoryPageQuery query = new CategoryPageQuery();
        query.setParentId(1L);
        query.setPage(1);
        query.setSize(1);
        PageResult<ConstantConfigCenterCategory> page = ccc.getCategoryPage(query);
        // 仅默认(1) + 两个子分类，按 parentId=1 过滤后命中 2 条
        assertEquals(2, page.getTotal());
        assertEquals(1, page.getList().size());
    }

    // ────────────────────── 工具 ──────────────────────

    private ConstantConfigCenterItem item(String name, String key, String value) {
        ConstantConfigCenterItem item = new ConstantConfigCenterItem();
        item.setConfigName(name);
        item.setKey(key);
        item.setValue(value);
        item.setValueType(ConstantConfigCenterValueType.STRING);
        return item;
    }
}