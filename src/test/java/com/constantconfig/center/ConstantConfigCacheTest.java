package com.constantconfig.center;

import com.constantconfig.center.api.ConstantConfigCenter;
import com.constantconfig.center.cache.ConstantConfigCache;
import com.constantconfig.center.model.ConstantConfigValueType;
import com.constantconfig.center.model.command.CategorySaveReqVO;
import com.constantconfig.center.model.command.ConfigSaveReqVO;
import com.constantconfig.center.model.entity.ConstantConfigDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 读侧缓存专项测试（验证 TTL 命中回填 + 写后事件失效）
 *
 * <p>直接断言 {@link ConstantConfigCache} 内部索引状态，确认：</p>
 * <ul>
 *   <li>读操作首次命中回填、后续命中所读值即为库内最新值；</li>
 *   <li>配置新增 / 更新 / 删除 -> 事件 -> 相应索引被失效；</li>
 *   <li>分类树缓存 -> 分类新增 -> 整体失效。</li>
 * </ul>
 */
@SpringBootTest(classes = ConstantConfigCenterTestApplication.class)
class ConstantConfigCacheTest {

    @Autowired
    private ConstantConfigCenter ccc;

    @Autowired
    private ConstantConfigCache cache;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM constant_config_center");
        jdbcTemplate.update("DELETE FROM constant_config_category WHERE category_id != 1");
    }

    private ConfigSaveReqVO cfg(String name, String key, String value) {
        ConfigSaveReqVO c = new ConfigSaveReqVO();
        c.setConfigName(name);
        c.setKey(key);
        c.setValue(value);
        c.setValueType(ConstantConfigValueType.STRING);
        return c;
    }

    // ────────────────────── 配置：回填 + 失效 ──────────────────────

    @Test
    void readPopulatesCacheAndUpdateInvalidates() {
        ccc.createConfig(cfg("k1", "ck.cache.1", "v1"));
        // 新增不缓存
        assertNull(cache.getConfigByKey("ck.cache.1"));

        // 首次读命中回填；断言命中值即库内值
        assertEquals("v1", ccc.getConfig("ck.cache.1"));
        assertNotNull(cache.getConfigByKey("ck.cache.1"));

        // 第二次读命中缓存（仍得最新值）
        assertEquals("v1", ccc.getConfig("ck.cache.1"));

        // 更新 -> 事件 UPDATED -> 整体失效
        ccc.updateConfig(cfg("k1", "ck.cache.1", "v2"));
        assertNull(cache.getConfigByKey("ck.cache.1"));
        // 失效后回源读到新值
        assertEquals("v2", ccc.getConfig("ck.cache.1"));
    }

    @Test
    void deleteInvalidatesConfigCache() {
        ccc.createConfig(cfg("k", "ck.cache.del", "v"));
        assertEquals("v", ccc.getConfig("ck.cache.del")); // 回填
        assertNotNull(cache.getConfigByKey("ck.cache.del"));

        ccc.deleteConfig("ck.cache.del");
        assertNull(cache.getConfigByKey("ck.cache.del"));
        assertNull(ccc.getConfig("ck.cache.del"));
    }

    @Test
    void cacheStoresDefensiveCopyIsolatingExternalMutation() {
        // 直接构造 DO 入缓存，外部篡改原对象不应污染缓存内已存的快照
        ConstantConfigDO original = new ConstantConfigDO();
        original.setKey("iso.key");
        original.setConfigName("隔离配置");
        original.setValue("v1");
        cache.putConfigByKey("iso.key", original);
        cache.putConfigByName("隔离配置", original);

        original.setValue("mutated"); // 篡改原对象

        assertEquals("v1", cache.getConfigByKey("iso.key").getValue());
        assertEquals("v1", cache.getConfigByName("隔离配置").getValue());
    }

    @Test
    void getKeyByConfigNamePopulatesAndClearsOnUpdate() {
        ccc.createConfig(cfg("名称A", "ck.cache.name", "v1"));
        assertEquals("ck.cache.name", ccc.getKeyByConfigName("名称A"));
        assertNotNull(cache.getConfigByName("名称A"));

        // 更新发生名称变更 -> 整体失效双索引
        ccc.updateConfig(cfg("名称B", "ck.cache.name", "v2"));
        assertNull(cache.getConfigByName("名称B"));
        assertNull(cache.getConfigByName("名称A"));
        assertEquals("ck.cache.name", ccc.getKeyByConfigName("名称B"));
    }

    // ────────────────────── 分类：树回填 + 失效 ──────────────────────

    @Test
    void categoryTreeCachedAndClearedOnCategoryChange() {
        ccc.listCategoryTree(); // 回填
        assertNotNull(cache.getCategoryAll());

        ccc.createCategory(category("缓存子分类", 1L, 0));
        // 分类新增 -> 事件 -> 树整体失效
        assertNull(cache.getCategoryAll());
        // 失效后回源组装含新分类的树，并重新回填
        assertEquals(1, ccc.listCategoryTree().size());
        assertNotNull(cache.getCategoryAll());
    }

    private CategorySaveReqVO category(String name, Long parentId, Integer sort) {
        CategorySaveReqVO c = new CategorySaveReqVO();
        c.setCategoryName(name);
        c.setCategoryParentId(parentId);
        c.setSort(sort);
        return c;
    }
}