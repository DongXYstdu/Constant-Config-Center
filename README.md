# constant-config-spring-boot-starter

<p align="center">
  <a href="https://central.sonatype.com/artifact/io.github.dongxystdu/constant-config-spring-boot-starter">
    <img src="https://img.shields.io/maven-central/v/io.github.dongxystdu/constant-config-spring-boot-starter?label=Maven%20Central" alt="Maven Central" />
  </a>
  <a href="https://github.com/DongXYstdu/Constant-Config-Center/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/DongXYstdu/Constant-Config-Center" alt="License" />
  </a>
  <a href="https://github.com/DongXYstdu/Constant-Config-Center">
    <img src="https://img.shields.io/github/stars/DongXYstdu/Constant-Config-Center?style=social" alt="GitHub stars" />
  </a>
</p>

独立通用的 **常量配置中心** Spring Boot Starter：把散落在代码里的静态常量 / 枚举 / 阈值等集中到数据库，实现**改参数不用改代码、不用重新编译部署**，并支持跨项目复用。

## 功能特性

- **开箱即用**：自动装配（`@AutoConfiguration` + `AutoConfiguration.imports`），引入依赖 + 建表即可使用。
- **配置 / 分类双套对称 API**：管理面提供 `createConfig / updateConfig / deleteConfig / getConfigList / getConfigPage`，分类同理（`createCategory / updateCategory / deleteCategory / getCategoryList / getCategoryPage / listCategoryTree`），支持 CRUD、列表与分页。
- **统一异常体系**：基类 `ConstantConfigException`，派生冲突 / 缺失 / 序列化三类语义异常（均继承 `IllegalArgumentException`），调用方可精确捕获。
- **纯新增写入**：`createConfig` / `updateConfig` 均为非覆盖式；`config_name` 或 `key` 任何唯一键冲突抛 `ConstantConfigConflictException`（携带已存在行主键 id），不做静默覆盖。
- **值类型支持**：`STRING` 直接存文本；`LIST` / `MAP` 以 JSON 文本存储，读取用 `TypeReference` 反序列化以保留泛型。
- **名称反查**：`config_name`（常量配置名称）全局唯一，可用 `getKeyByConfigName` 由名称反查程序取值用的 `key`。
- **树形分类**：邻接表 + 物化路径，支持分类的创建 / 更新 / 删除（仅叶子）/ 列表 / 分页与树查询。
- **可扩展**：存储后端 SPI（`ConstantConfigProvider` / `ConstantConfigCategoryProvider`），可整体替换默认 JDBC 实现。

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
        // ── 新增配置（纯新增、冲突抛异常） ──
        ConstantConfig item = new ConstantConfig();
        item.setConfigName("比功率有效区间");
        item.setKey("iot.power.range");
        item.setValue("[1,50]");                 // STRING：直接载入文本
        item.setValueType(ConstantConfigValueType.STRING);
        ccc.createConfig(item);

        // ── 读取（不存在返回 null / 默认值） ──
        String range = ccc.getConfig("iot.power.range");
        String fallback = ccc.getConfig("iot.power.range", "[1,50]");

        // ── 名称反查 key ──
        String key = ccc.getKeyByConfigName("比功率有效区间");

        // ── LIST / MAP（以 JSON 文本存储，用 valueObject 传入集合/映射） ──
        ConstantConfig listItem = new ConstantConfig();
        listItem.setConfigName("允许设备");
        listItem.setKey("iot.allow.devices");
        listItem.setValueObject(List.of("d1", "d2"));
        listItem.setValueType(ConstantConfigValueType.LIST);
        ccc.createConfig(listItem);
        List<String> devices = ccc.getConfig("iot.allow.devices", new TypeReference<List<String>>() {});

        // ── 更新（按 key 定位，version 自增；缺失抛 NotFoundException） ──
        ConstantConfig upd = new ConstantConfig();
        upd.setKey("iot.power.range");
        upd.setValue("[1,60]");
        ccc.updateConfig(upd);

        // ── 列表 / 分页查询 ──
        List<ConstantConfig> list = ccc.getConfigList(null, "power");   // 关键字过滤
        ConfigPageQuery q = new ConfigPageQuery();
        q.setPage(1);
        q.setSize(10);
        PageResult<ConstantConfig> page = ccc.getConfigPage(q);

        // ── 删除（按 key 定位；缺失抛 NotFoundException） ──
        ccc.deleteConfig("iot.power.range");

        // ── 分类管理（树形） ──
        Long sensorId = ccc.createCategory(category("传感器", 1L, 0));
        List<ConstantConfigCategory> tree = ccc.listCategoryTree();
        ccc.deleteCategory(sensorId);   // 仅允许删除叶子分类
    }
}
```

### 5. 统一异常处理

所有业务异常均继承运行时异常基类 `ConstantConfigException`（间接继承 `IllegalArgumentException`），可按需精确捕获：

| 异常 | 触发场景 | 关键信息 |
| --- | --- | --- |
| `ConstantConfigConflictException` | 写入时 `config_name` / `key` 唯一键冲突 | `getConflictField()` / `getConflictValue()` / `getExistingId()` |
| `ConstantConfigNotFoundException` | 更新 / 删除的目标不存在 | 定位键及值 |
| `ConstantConfigSerializationException` | LIST / MAP 序列化 / 反序列化失败 | 原始文本与目标类型 |

```java
try {
    ccc.createConfig(item("比功率有效区间", "iot.power.range.other", "[2,60]"));
} catch (ConstantConfigConflictException e) {
    // e.getConflictField()  → "config_name" 或 "key"
    // e.getConflictValue()  → 冲突的具体值
    // e.getExistingId()     → 已存在行的主键 id，便于定位冲突记录
    Long existingId = e.getExistingId();
}
```

## 核心 API 一览

### 配置管理

| 类别 | 方法 | 说明 |
| --- | --- | --- |
| 读取 | `String getConfig(String key)` | 按 key 取值（STRING 直返文本，LIST/MAP 返回 JSON 文本），不存在返回 `null` |
| 读取 | `String getConfig(String key, String defaultValue)` | 带默认值 |
| 读取 | `<T> T getConfig(String key, TypeReference<T> typeRef)` | 反序列化为强类型（保留泛型） |
| 反查 | `String getKeyByConfigName(String configName)` | 名称反查 key（`config_name` 全局唯一） |
| 新增 | `Long createConfig(ConstantConfig item)` | 纯新增，冲突抛异常，返回主键 id |
| 更新 | `void updateConfig(ConstantConfig item)` | 按 `key` 合并补丁更新，缺失抛 `NotFoundException` |
| 删除 | `void deleteConfig(String key)` | 按 `key` 删除，缺失抛 `NotFoundException` |
| 列表 | `List<ConstantConfig> getConfigList(Long categoryId, String keyword)` | 按分类 / 关键字过滤 |
| 分页 | `PageResult<ConstantConfig> getConfigPage(ConfigPageQuery query)` | 分页查询（含总数） |

### 分类管理

| 类别 | 方法 | 说明 |
| --- | --- | --- |
| 新增 | `Long createCategory(ConstantConfigCategory category)` | 自动生成 path / level |
| 更新 | `void updateCategory(ConstantConfigCategory category)` | 按 `categoryId` 更新 |
| 删除 | `void deleteCategory(Long categoryId)` | 仅允许删除叶子分类 |
| 列表 | `List<ConstantConfigCategory> getCategoryList(Long parentId, String keyword)` | 按父ID / 关键字过滤 |
| 分页 | `PageResult<ConstantConfigCategory> getCategoryPage(CategoryPageQuery query)` | 分页查询（含总数） |
| 树 | `List<ConstantConfigCategory> listCategoryTree()` | 全部分类组装为树（含 `children`） |

更多细节见 [项目技术文档](../docs/【常量配置中心】-【SpringBoot-Starter动态配置中心】-【v1.0】-项目技术文档.md)。

## 运行测试

```bash
mvn test
```

测试基于 H2 内存库（MySQL 兼容模式），覆盖读写、唯一键冲突异常（含 `existingId` 校验）、更新/删除缺失（`NotFoundException`）、LIST/MAP 序列化、非法 JSON、名称反查、列表/分页、分类 CRUD 与树管理。
