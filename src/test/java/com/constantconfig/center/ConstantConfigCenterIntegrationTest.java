package com.constantconfig.center;

import com.constantconfig.center.api.ConstantConfigCenter;
import com.constantconfig.center.query.CategoryPageReqVO;
import com.constantconfig.center.query.ConfigPageReqVO;
import com.constantconfig.center.query.PageResult;
import com.constantconfig.center.model.ConstantConfigValueType;
import com.constantconfig.center.model.command.CategorySaveReqVO;
import com.constantconfig.center.model.command.ConfigSaveReqVO;
import com.constantconfig.center.model.view.CategoryRespVO;
import com.constantconfig.center.model.view.ConfigRespVO;
import com.constantconfig.center.exception.ConstantConfigConflictException;
import com.constantconfig.center.exception.ConstantConfigException;
import com.constantconfig.center.exception.ConstantConfigNotFoundException;
import com.constantconfig.center.exception.ConstantConfigSerializationException;
import com.constantconfig.center.exception.ConstantConfigVersionMismatchException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;

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
 * <p>覆盖新门面 API（Command / View 契约）：STRING / LIST / MAP 读写、唯一键冲突
 * （ConflictException + existingId）、更新/删除目标不存在（NotFoundException）、
 * 列表与分页、分类 CRUD 与树。</p>
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

        ConstantConfigConflictException ex = assertThrows(
                ConstantConfigConflictException.class,
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

        ConstantConfigConflictException ex = assertThrows(
                ConstantConfigConflictException.class,
                () -> ccc.createConfig(item("配置B", "key.x", "v2")));

        assertEquals("key", ex.getConflictField());
        assertEquals("key.x", ex.getConflictValue());
        Long existingId = jdbcTemplate.queryForObject(
                "SELECT id FROM constant_config_center WHERE config_key = ?", Long.class, "key.x");
        assertNotNull(existingId);
        assertEquals(existingId, ex.getExistingId());
    }

    @Test
    void conflictDoesNotOverwriteExistingRow() {
        ccc.createConfig(item("配置A", "key.a", "v1"));
        assertThrows(ConstantConfigConflictException.class,
                () -> ccc.createConfig(item("配置A", "key.b", "v2")));
        assertEquals("v1", ccc.getConfig("key.a"));
        assertNull(ccc.getConfig("key.b"));
    }

    // ────────────────────── 门面层参数校验（A2） ──────────────────────

    @Test
    void createConfigWithBlankKeyThrowsSemanticError() {
        assertThrows(ConstantConfigException.class, () -> ccc.createConfig(item("空键", "  ", "v1")));
    }

    @Test
    void createConfigWithBlankNameThrowsSemanticError() {
        assertThrows(ConstantConfigException.class, () -> ccc.createConfig(item(" ", "key.blank.name", "v1")));
    }

    @Test
    void createConfigWithNullValueThrowsSemanticError() {
        // 列 NOT NULL 本就拒绝，此处改为门面层语义异常而非裸约束异常
        ConfigSaveReqVO c = item("空值配置", "key.null.value", null);
        assertThrows(ConstantConfigException.class, () -> ccc.createConfig(c));
    }

    @Test
    void createConfigWithNonExistentCategoryThrowsSemanticError() {
        ConfigSaveReqVO c = item("孤儿分类配置", "key.bad.category", "v1");
        c.setCategoryId(999999L);
        assertThrows(ConstantConfigException.class, () -> ccc.createConfig(c));
    }

    @Test
    void updateConfigToNonExistentCategoryThrowsSemanticError() {
        ccc.createConfig(item("原配置", "key.move.category", "v1"));
        ConfigSaveReqVO c = item("原配置", "key.move.category", "v2");
        c.setCategoryId(999999L);
        assertThrows(ConstantConfigException.class, () -> ccc.updateConfig(c));
    }

    // ────────────────────── LIST / MAP 序列化 ──────────────────────

    @Test
    void listReadWrite() {
        ConfigSaveReqVO listItem = item("允许列表", "iot.allow.list", null);
        listItem.setValue(Arrays.asList("a", "b", "c"));
        listItem.setValueType(ConstantConfigValueType.LIST);
        ccc.createConfig(listItem);

        List<String> list = ccc.getConfig("iot.allow.list", new TypeReference<List<String>>() {});
        assertEquals(Arrays.asList("a", "b", "c"), list);
    }

    @Test
    void mapReadWrite() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("scale", 0.1);
        map.put("enabled", true);
        ConfigSaveReqVO mapItem = item("控制参数", "iot.param", null);
        mapItem.setValue(map);
        mapItem.setValueType(ConstantConfigValueType.MAP);
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
        assertThrows(ConstantConfigSerializationException.class,
                () -> ccc.getConfig("iot.bad", new TypeReference<List<?>>() {}));
    }

    // ────────────────────── 更新 / 删除 ──────────────────────

    @Test
    void updateConfigModifiesValueAndIncrementsVersion() {
        ccc.createConfig(item("目标压力", "iot.pressure.target", "0.1"));
        Long versionBefore = jdbcTemplate.queryForObject(
                "SELECT version FROM constant_config_center WHERE config_key = ?", Long.class, "iot.pressure.target");

        ConfigSaveReqVO upd = item("目标压力2", "iot.pressure.target", "0.2");
        upd.setRemark("调参");
        ccc.updateConfig(upd);

        assertEquals("0.2", ccc.getConfig("iot.pressure.target"));
        Long versionAfter = jdbcTemplate.queryForObject(
                "SELECT version FROM constant_config_center WHERE config_key = ?", Long.class, "iot.pressure.target");
        assertEquals(versionBefore + 1, versionAfter);
    }

    @Test
    void updateConfigWithStaleVersionThrowsVersionMismatch() {
        ccc.createConfig(item("版本测试", "iot.version.key", "v0"));
        // 模拟并发：另一会话已把版本推进到 1
        jdbcTemplate.update(
                "UPDATE constant_config_center SET version = version + 1 WHERE config_key = ?", "iot.version.key");

        // 用过期的期望版本 0 提交，应被乐观锁拦截
        ConfigSaveReqVO stale = item("版本测试", "iot.version.key", "v-later");
        stale.setVersion(0L);
        ConstantConfigVersionMismatchException ex = assertThrows(
                ConstantConfigVersionMismatchException.class, () -> ccc.updateConfig(stale));

        assertEquals("iot.version.key", ex.getKey());
        assertEquals(Long.valueOf(0L), ex.getExpectedVersion());
        assertEquals(Long.valueOf(1L), ex.getActualVersion());
        // 值未被覆盖
        assertEquals("v0", ccc.getConfig("iot.version.key"));
    }

    @Test
    void updateConfigNameConflictThrows() {
        ccc.createConfig(item("A", "k1", "v1"));
        ccc.createConfig(item("B", "k2", "v2"));
        // 把 k1 改成已占用的名称 B
        ConfigSaveReqVO upd = new ConfigSaveReqVO();
        upd.setKey("k1");
        upd.setConfigName("B");
        assertThrows(ConstantConfigConflictException.class, () -> ccc.updateConfig(upd));
    }

    @Test
    void updateConfigMissingThrowsNotFound() {
        ConfigSaveReqVO upd = item("不存在", "no.such.key", "x");
        assertThrows(ConstantConfigNotFoundException.class, () -> ccc.updateConfig(upd));
    }

    @Test
    void deleteConfig() {
        ccc.createConfig(item("c1", "k1", "v1"));
        ccc.deleteConfig("k1");
        assertNull(ccc.getConfig("k1"));
        // 重复删除不存在 → NotFoundException
        assertThrows(ConstantConfigNotFoundException.class, () -> ccc.deleteConfig("k1"));
    }

    // ────────────────────── 列表 / 分页 ──────────────────────

    @Test
    void listConfigsFilteredByCategoryAndKeyword() {
        ccc.createConfig(item("水位阈值", "iot.water.high", "80"));
        ccc.createConfig(item("压力上限", "iot.pressure.max", "1.5"));
        ccc.createConfig(item("温度下限", "iot.temp.min", "-20"));

        // 按关键字（匹配 key）
        List<ConfigRespVO> list = ccc.getConfigList(null, "press");
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

        ConfigPageReqVO q1 = new ConfigPageReqVO();
        q1.setPage(1);
        q1.setSize(2);
        PageResult<ConfigRespVO> p1 = ccc.getConfigPage(q1);
        assertEquals(3, p1.getTotal());
        assertEquals(2, p1.getList().size());
        assertEquals(1, p1.getPage());
        assertEquals(2, p1.getSize());

        ConfigPageReqVO q2 = new ConfigPageReqVO();
        q2.setPage(2);
        q2.setSize(2);
        assertEquals(1, ccc.getConfigPage(q2).getList().size());
    }

    // ────────────────────── 分类管理（CRUD + 树） ──────────────────────

    private CategorySaveReqVO category(String name, Long parentId, Integer sort) {
        CategorySaveReqVO category = new CategorySaveReqVO();
        category.setCategoryName(name);
        category.setCategoryParentId(parentId);
        category.setSort(sort);
        return category;
    }

    @Test
    void createCategoryGeneratesPathAndLevel() {
        Long childId = ccc.createCategory(category("传感器", 1L, 0));

        List<CategoryRespVO> roots = ccc.listCategoryTree();
        assertEquals(1, roots.size());
        List<CategoryRespVO> children = roots.get(0).getChildren();
        assertEquals(1, children.size());
        assertEquals("传感器", children.get(0).getCategoryName());
        assertEquals("/1/" + childId, children.get(0).getPath());
        assertEquals(2, children.get(0).getLevel());
    }

    @Test
    void updateCategoryRenames() {
        Long id = ccc.createCategory(category("传感器", 1L, 0));
        CategorySaveReqVO upd = new CategorySaveReqVO();
        upd.setCategoryId(id);
        upd.setCategoryName("功率计");
        ccc.updateCategory(upd);

        assertEquals("功率计", ccc.getCategoryList(1L, null).get(0).getCategoryName());
    }

    @Test
    void categoryNamesAreUniqueAcrossSiblings() {
        ccc.createCategory(category("重复分类", 1L, 0));
        assertThrows(ConstantConfigException.class,
                () -> ccc.createCategory(category("重复分类", 1L, 0)));
    }

    @Test
    void deleteCategoryWithChildrenThrows() {
        ccc.createCategory(category("子分类", 1L, 0));
        assertThrows(ConstantConfigException.class,
                () -> ccc.deleteCategory(1L));
    }

    @Test
    void categoryForeignKeyRejectsOrphanInsert() {
        // 绕过门面直插：指向不存在分类的配置行，应被外键兜底拦截（DataIntegrityViolation）
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO constant_config_center "
                        + "(category_id, config_name, config_key, config_value, value_type, version, create_time, update_time) "
                        + "VALUES (999999, '孤儿配置', 'orphan.key', 'v', 'STRING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"));
    }

    @Test
    void deleteCategoryWithConfigsThrows() {
        // 分类下挂载配置后，删除该分类应被拒绝（防止配置变孤儿）
        Long categoryId = ccc.createCategory(category("有配置分类", 1L, 0));
        ConfigSaveReqVO cfg = item("分类内配置", "key.in.category", "v1");
        cfg.setCategoryId(categoryId);
        ccc.createConfig(cfg);
        assertThrows(ConstantConfigException.class, () -> ccc.deleteCategory(categoryId));
    }

    @Test
    void deleteLeafCategorySucceeds() {
        Long childId = ccc.createCategory(category("叶子分类", 1L, 0));
        ccc.deleteCategory(childId);
        assertTrue(ccc.listCategoryTree().get(0).getChildren().isEmpty());
        assertThrows(ConstantConfigNotFoundException.class, () -> ccc.deleteCategory(childId));
    }

    @Test
    void pageCategories() {
        ccc.createCategory(category("传感器", 1L, 0));
        ccc.createCategory(category("仪表", 1L, 0));

        CategoryPageReqVO query = new CategoryPageReqVO();
        query.setParentId(1L);
        query.setPage(1);
        query.setSize(1);
        PageResult<CategoryRespVO> page = ccc.getCategoryPage(query);
        // 仅默认(1) + 两个子分类，按 parentId=1 过滤后命中 2 条
        assertEquals(2, page.getTotal());
        assertEquals(1, page.getList().size());
    }

    // ────────────────────── 工具 ──────────────────────

    private ConfigSaveReqVO item(String name, String key, Object value) {
        ConfigSaveReqVO item = new ConfigSaveReqVO();
        item.setConfigName(name);
        item.setKey(key);
        item.setValue(value);
        item.setValueType(ConstantConfigValueType.STRING);
        return item;
    }
}