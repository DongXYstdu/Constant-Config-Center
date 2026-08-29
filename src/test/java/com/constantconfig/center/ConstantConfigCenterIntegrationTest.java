package com.constantconfig.center;

import com.constantconfig.center.api.ConstantConfigCenter;
import com.constantconfig.center.core.ConstantConfigCenterCategory;
import com.constantconfig.center.core.ConstantConfigCenterConflictException;
import com.constantconfig.center.core.ConstantConfigCenterValueType;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 常量配置中心集成测试（H2 内存库，MySQL 兼容模式）
 *
 * <p>覆盖规则（2026-08-29 修正）：{@code config_name} 或 {@code key} 任一唯一键冲突时，
 * 写入失败并抛 {@link ConstantConfigCenterConflictException}，携带已存在行主键 id。</p>
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
    void setAndGetString() {
        ccc.setConfig("比功率有效区间", "iot.power.range", "[1,50]");
        assertEquals("[1,50]", ccc.getConfig("iot.power.range"));
        // 带默认值重载
        assertEquals("[1,50]", ccc.getConfig("iot.power.range", "fallback"));
    }

    @Test
    void getConfigReturnsDefaultWhenMissing() {
        assertNull(ccc.getConfig("not.exists"));
        assertEquals("fallback", ccc.getConfig("not.exists", "fallback"));
    }

    // ────────────────────── 唯一键冲突规则（核心） ──────────────────────

    @Test
    void configNameConflictThrowsConflictExceptionWithExistingId() {
        ccc.setConfig("配置A", "key.a", "v1");

        // 同名不同 key：config_name 冲突
        ConstantConfigCenterConflictException ex = assertThrows(
                ConstantConfigCenterConflictException.class,
                () -> ccc.setConfig("配置A", "key.b", "v2"));

        assertEquals("config_name", ex.getConflictField());
        assertEquals("配置A", ex.getConflictValue());
        // existingId 必须是已存在行（config_name='配置A'）的主键 id
        Long existingId = jdbcTemplate.queryForObject(
                "SELECT id FROM constant_config_center WHERE config_name = ?", Long.class, "配置A");
        assertNotNull(existingId);
        assertEquals(existingId, ex.getExistingId());
    }

    @Test
    void keyConflictThrowsConflictExceptionWithExistingId() {
        ccc.setConfig("配置A", "key.x", "v1");

        // 不同名同 key：key 冲突
        ConstantConfigCenterConflictException ex = assertThrows(
                ConstantConfigCenterConflictException.class,
                () -> ccc.setConfig("配置B", "key.x", "v2"));

        assertEquals("key", ex.getConflictField());
        assertEquals("key.x", ex.getConflictValue());
        Long existingId = jdbcTemplate.queryForObject(
                "SELECT id FROM constant_config_center WHERE `key` = ?", Long.class, "key.x");
        assertNotNull(existingId);
        assertEquals(existingId, ex.getExistingId());
    }

    @Test
    void conflictDoesNotOverwriteExistingRow() {
        ccc.setConfig("配置A", "key.a", "v1");
        // 冲突写入失败后，原记录保持不变，且未新增脏数据
        assertThrows(ConstantConfigCenterConflictException.class,
                () -> ccc.setConfig("配置A", "key.b", "v2"));
        assertEquals("v1", ccc.getConfig("key.a"));
        assertNull(ccc.getConfig("key.b"));
    }

    // ────────────────────── LIST / MAP 序列化 ──────────────────────

    @Test
    void listReadWrite() {
        ccc.setConfig("允许列表", "iot.allow.list",
                Arrays.asList("a", "b", "c"), ConstantConfigCenterValueType.LIST);

        List<?> list = ccc.getConfig("iot.allow.list", List.class);
        assertEquals(3, list.size());
        assertEquals("a", list.get(0));
        assertEquals("c", list.get(2));
    }

    @Test
    void mapReadWrite() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("scale", 0.1);
        map.put("enabled", true);
        ccc.setConfig("控制参数", "iot.param", map, ConstantConfigCenterValueType.MAP);

        Map<?, ?> result = ccc.getConfig("iot.param", Map.class);
        assertEquals(0.1, ((Number) result.get("scale")).doubleValue());
        assertEquals(true, result.get("enabled"));
    }

    @Test
    void invalidJsonForTypedReadThrowsIllegalArgumentException() {
        // STRING 值原样返回
        ccc.setConfig("坏值", "iot.bad", "not-json");
        assertEquals("not-json", ccc.getConfig("iot.bad"));
        // 按 LIST 反序列化失败 → IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> ccc.getConfig("iot.bad", List.class));
    }

    // ────────────────────── 名称反查 / 罗列 / 删除 ──────────────────────

    @Test
    void getKeyByConfigName() {
        ccc.setConfig("目标压力", "iot.pressure.target", "0.1");
        assertEquals("iot.pressure.target", ccc.getKeyByConfigName("目标压力"));
        assertNull(ccc.getKeyByConfigName("不存在的名称"));
    }

    @Test
    void getAllConfigByCategory() {
        ccc.setConfig("c1", "k1", "v1");
        ccc.setConfig("c2", "k2", "v2");
        Map<String, String> all = ccc.getAllConfig(ConstantConfigCenter.DEFAULT_CATEGORY_ID);
        assertEquals(2, all.size());
        assertEquals("v1", all.get("k1"));
        assertEquals("v2", all.get("k2"));
        // 无数据分类返回空 Map
        assertTrue(ccc.getAllConfig(9999L).isEmpty());
    }

    @Test
    void deleteConfig() {
        ccc.setConfig("c1", "k1", "v1");
        assertTrue(ccc.deleteConfig("k1"));
        assertFalse(ccc.deleteConfig("k1"));
        assertNull(ccc.getConfig("k1"));
    }

    // ────────────────────── 分类管理（树形） ──────────────────────

    @Test
    void createCategoryGeneratesPathAndLevel() {
        Long childId = ccc.createCategory("传感器", 1L, 0);
        assertNotNull(childId);

        List<ConstantConfigCenterCategory> roots = ccc.listCategoryTree();
        assertEquals(1, roots.size());
        ConstantConfigCenterCategory root = roots.get(0);
        assertEquals("默认", root.getCategoryName());
        assertEquals(1, root.getChildren().size());

        ConstantConfigCenterCategory child = root.getChildren().get(0);
        assertEquals("传感器", child.getCategoryName());
        assertEquals("/1/" + childId, child.getPath());
        assertEquals(2, child.getLevel());
    }

    @Test
    void deleteCategoryWithChildrenThrows() {
        ccc.createCategory("子分类", 1L, 0);
        // 根分类有子分类，不可删除
        assertThrows(IllegalArgumentException.class,
                () -> ccc.deleteCategory(ConstantConfigCenter.DEFAULT_CATEGORY_ID));
    }

    @Test
    void deleteLeafCategorySucceeds() {
        Long childId = ccc.createCategory("叶子分类", 1L, 0);
        assertTrue(ccc.deleteCategory(childId));
        assertTrue(ccc.listCategoryTree().get(0).getChildren().isEmpty());
    }

    @Test
    void createCategoryDuplicateNameThrows() {
        ccc.createCategory("重复分类", 1L, 0);
        assertThrows(IllegalArgumentException.class,
                () -> ccc.createCategory("重复分类", 1L, 0));
    }

    @Test
    void createCategoryParentNotFoundThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ccc.createCategory("孤儿分类", 999L, 0));
    }
}
