# constant-config-spring-boot-starter

独立通用的 **常量配置中心** Spring Boot Starter：把散落在代码里的静态常量 / 枚举 / 阈值等集中到数据库，实现**改参数不用改代码、不用重新编译部署**，并支持跨项目复用。

## 功能特性

- **开箱即用**：自动装配（`@AutoConfiguration` + `AutoConfiguration.imports`），引入依赖 + 建表即可使用。
- **纯新增写入**：`setConfig` 系列为纯新增，`config_name` 或 `key` 任一全局唯一键冲突时抛 `ConstantConfigCenterConflictException`（携带已存在行主键 id），不做静默覆盖。
- **值类型支持**：`STRING` 直接存文本；`LIST` / `MAP` 以 JSON 文本存储，读取时按类型反序列化。
- **名称反查**：`config_name`（常量配置名称）全局唯一，可用 `getKeyByConfigName` 由名称反查程序取值用的 `key`。
- **树形分类**：邻接表 + 物化路径，支持分类的创建 / 删除（仅叶子）/ 树查询。
- **可扩展**：存储后端 SPI（`ConstantConfigCenterProvider` / `ConstantConfigCenterCategoryProvider`），可整体替换默认 JDBC 实现。

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.github.dongxystdu</groupId>
    <artifactId>constant-config-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 初始化数据库

执行建表脚本 [constant_config_center.sql](sql/constant_config_center.sql)（含分类表 + 配置键值表）。

> 注意：脚本会先插入 `category_id=1` 的默认分类（名称“默认”），请保持该行存在，否则默认分类 API 会失效。

### 3. 配置（可选，均有默认值）

```yaml
spring:
  constant-config-center:
    enabled: true          # 是否启用，默认 true
    table: constant_config_center      # 配置表名，默认 constant_config_center
    category-table: constant_config_category  # 分类表名，默认 constant_config_category
```

无需配置数据源以外的任何东西：starter 会自动装配 `JdbcTemplate`、存储实现与门面 Bean。

### 4. 使用示例

```java
@Service
public class DemoService {

    private final ConstantConfigCenter ccc;

    public DemoService(ConstantConfigCenter ccc) {
        this.ccc = ccc;
    }

    public void demo() {
        // ── 写入 STRING 配置（纯新增） ──
        ccc.setConfig("比功率有效区间", "iot.power.range", "[1,50]");

        // ── 读取（不存在返回 null / 默认值） ──
        String range = ccc.getConfig("iot.power.range");
        String fallback = ccc.getConfig("iot.power.range", "[1,50]");

        // ── 名称反查 key ──
        String key = ccc.getKeyByConfigName("比功率有效区间");

        // ── LIST / MAP（以 JSON 文本存储） ──
        ccc.setConfig("允许设备", "iot.allow.devices",
                List.of("d1", "d2"), ConstantConfigCenterValueType.LIST);
        List<String> devices = ccc.getConfig("iot.allow.devices", List.class);

        Map<String, Object> params = new HashMap<>();
        params.put("scale", 0.1);
        ccc.setConfig("控制参数", "iot.ctrl.params",
                params, ConstantConfigCenterValueType.MAP);
        Map<?, ?> ctrl = ccc.getConfig("iot.ctrl.params", Map.class);

        // ── 分类下罗列 / 删除 ──
        Map<String, String> all = ccc.getAllConfig(ConstantConfigCenter.DEFAULT_CATEGORY_ID);
        boolean deleted = ccc.deleteConfig("iot.ctrl.params");

        // ── 分类管理（树形） ──
        Long sensorId = ccc.createCategory("传感器", 1L, 0);
        List<ConstantConfigCenterCategory> tree = ccc.listCategoryTree();
        ccc.deleteCategory(sensorId);   // 仅允许删除叶子分类
    }
}
```

### 5. 唯一键冲突处理（重要规则）

写入为**纯新增**：当 `config_name` 或 `key` 任一全局唯一键已被其它记录占用时，抛出 `ConstantConfigCenterConflictException`，可通过 `getExistingId()` 拿到已存在行的主键 id：

```java
try {
    ccc.setConfig("比功率有效区间", "iot.power.range.other", "[2,60]");
} catch (ConstantConfigCenterConflictException e) {
    // e.getConflictField()  → "config_name" 或 "key"
    // e.getConflictValue()  → 冲突的具体值
    // e.getExistingId()     → 已存在行的主键 id，便于定位冲突记录
    Long existingId = e.getExistingId();
}
```

## 核心 API 一览

| 类别 | 方法 | 说明 |
| --- | --- | --- |
| 读取 | `String getConfig(String key)` | 默认分类取值，不存在返回 `null` |
| 读取 | `String getConfig(String key, String defaultValue)` | 带默认值 |
| 读取 | `<T> T getConfig(String key, Class<T> type)` | 泛型取值（LIST/MAP 反序列化） |
| 反查 | `String getKeyByConfigName(String configName)` | 名称反查 key |
| 罗列 | `Map<String,String> getAllConfig(Long categoryId)` | 分类下全量扁平 Map |
| 写入 | `void setConfig(String configName, String key, String value)` | 纯新增，冲突抛异常 |
| 写入 | `<T> void setConfig(..., T value, ConstantConfigCenterValueType valueType)` | 带值类型写入 |
| 删除 | `boolean deleteConfig(String key)` | 删除，不存在返回 `false` |
| 分类 | `Long createCategory(name, parentId, sort)` / `deleteCategory(id)` / `listCategoryTree()` | 树形分类管理 |

更多细节见 [项目技术文档](../docs/【常量配置中心】-【SpringBoot-Starter动态配置中心】-【v1.0】-项目技术文档.md)。

## 运行测试

```bash
mvn test
```

测试基于 H2 内存库（MySQL 兼容模式），覆盖读写、唯一键冲突异常（含 `existingId` 校验）、LIST/MAP 序列化、非法 JSON、名称反查、罗列、删除及分类树管理。
