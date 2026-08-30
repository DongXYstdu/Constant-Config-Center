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
- **读侧内存缓存**：自研 TTL 缓存（默认开），命中 `getConfig` / `getKeyByConfigName` / `listCategoryTree`，写操作经变更事件自动失效，降低高频读对库的压测。
- **变更事件**：写成功后发布 `ConfigChangedEvent` / `CategoryChangedEvent`（Spring Event），可跨服务监听，本地缓存即靠同一事件失效。
- **可扩展**：存储后端 SPI（`ConstantConfigProvider` / `ConstantConfigCategoryProvider`），可整体替换默认 JDBC 实现；缓存 / 失效监听亦可整体替换。

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
    cache-enabled: true    # 读侧内存缓存开关，默认 true
    cache-ttl-seconds: 300 # 缓存 TTL 秒数，默认 300
    cache-max-size: 1000   # 缓存容量上限（>0 生效，超出停止写入新缓存），默认 1000
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
        ConfigPageReqVO q = new ConfigPageReqVO();
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
| 分页 | `PageResult<ConstantConfig> getConfigPage(ConfigPageReqVO query)` | 分页查询（含总数） |

### 分类管理

| 类别 | 方法 | 说明 |
| --- | --- | --- |
| 新增 | `Long createCategory(ConstantConfigCategory category)` | 自动生成 path / level |
| 更新 | `void updateCategory(ConstantConfigCategory category)` | 按 `categoryId` 更新 |
| 删除 | `void deleteCategory(Long categoryId)` | 仅允许删除叶子分类 |
| 列表 | `List<ConstantConfigCategory> getCategoryList(Long parentId, String keyword)` | 按父ID / 关键字过滤 |
| 分页 | `PageResult<ConstantConfigCategory> getCategoryPage(CategoryPageReqVO query)` | 分页查询（含总数） |
| 树 | `List<ConstantConfigCategory> listCategoryTree()` | 全部分类组装为树（含 `children`） |

更多细节见 [项目技术文档](../docs/【常量配置中心】-【SpringBoot-Starter动态配置中心】-【v1.0】-项目技术文档.md)。

## 运行测试

```bash
mvn test
```

测试基于 H2 内存库（MySQL 兼容模式），覆盖读写、唯一键冲突异常（含 `existingId` 校验）、更新/删除缺失（`NotFoundException`）、LIST/MAP 序列化、非法 JSON、名称反查、列表/分页、分类 CRUD 与树管理。

### 可信度覆盖清单（19 项）

以下为在 yudao `@SpringBootTest` 环境下跑通的 **19 个集成用例**，按 `@Order` 顺序分组，覆盖正常业务流程、分类树、异常语义与清理验证四类。

| # | 测试用例 | 覆盖点 |
| --- | --- | --- |
| 1 | 创建 STRING 类型配置 | 新增配置、返回主键、值写入正确 |
| 2 | 创建 LIST 类型配置（JSON 存储） | `valueObject` 集合写入、JSON 落库、`TypeReference` 读取还原 |
| 3 | 创建 MAP 类型配置（JSON 存储） | `valueObject` 映射写入、JSON 落库、泛型反序列化还原 |
| 4 | 带默认值读取 | `getConfig(key, default)` 命中返回配置、未命中返回默认值 |
| 5 | 名称反查 key | `getKeyByConfigName` 由唯一 `config_name` 反查 `key` |
| 6 | 更新配置（按 key 定位） | 合并补丁更新、值变更生效、缺失返回 `null` |
| 7 | 列表查询（按关键字过滤） | `getConfigList` 分类 / 关键字过滤行为 |
| 8 | 分页查询 | `getConfigPage` 分页总数与当前页数据正确 |
| 10 | 创建分类 | 自动生成 `path` / `level`、父子挂载 |
| 11 | 查询分类树 | `listCategoryTree` 树形组装、`children` 层级正确 |
| 12 | 分类分页查询 | `getCategoryPage` 分页结果正确 |
| 20 | 唯一键冲突异常（config_name 重复） | 抛 `ConstantConfigConflictException`，校验 `existingId` 定位 |
| 21 | 唯一键冲突异常（key 重复） | key 唯一键冲突同样被拦截 |
| 22 | 更新不存在的配置抛 NotFoundException | 缺失更新语义 |
| 23 | 删除不存在的配置抛 NotFoundException | 缺失删除语义 |
| 24 | LIST 类型非法 JSON 抛 SerializationException | 反序列化失败被封装为语义异常 |
| 30 | 删除配置 | 按 key 删除后不可再读到 |
| 31 | 删除叶子分类 | 叶子分类可删除、删除后查不到 |
| 32 | 非叶子分类删除应失败 | 存在子分类时删除抛异常，保护父级 |

> 编号按代码 `@Order` 保留连续槽位（如未使用的 9/13-19/25-29），便于后续扩展用例而无需重新编号。

## 集成到现有 Spring Boot 项目（以 yudao-boot-mini 为例）

以下是本 starter 在 [yudao-boot-mini-master-jdk17](https://github.com/YunaiV/yudao) 这类**多模块 Maven 项目**中的真实集成经验，可作为落地参考。

### 1. BOM 中管理版本

yudao 用 `yudao-dependencies` 统一管理三方依赖版本，在 [yudao-dependencies/pom.xml](yudao-dependencies/pom.xml) 的 `<dependencyManagement>` 中声明；业务模块不再写版本号。

```xml
<dependency>
    <groupId>io.github.dongxystdu</groupId>
    <artifactId>constant-config-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 业务模块引入

在需要使用的模块（如 `yudao-server`）添加依赖：

```xml
<dependency>
    <groupId>io.github.dongxystdu</groupId>
    <artifactId>constant-config-spring-boot-starter</artifactId>
</dependency>
```

### 3. 初始化数据库

在目标库（如 `yudao-cc-test-1-0-0`）执行 [constant_config_center.sql](sql/constant_config_center.sql)。**务必保证默认分类 `category_id=1`（名称“默认”）已插入** —— 建表脚本末尾自带该 INSERT，若仅执行了 CREATE TABLE 而漏掉 INSERT，创建分类会抛 `ConstantConfigException: 父分类不存在 categoryId=1`。可校验：

```sql
SELECT * FROM constant_config_category WHERE category_id = 1;
```

### 4. 多模块运行单个模块的测试

yudao 是 reactor 多模块工程，`yudao-server` 依赖 `yudao-module-system`、`yudao-module-infra` 等本地 SNAPSHOT 模块，单独 `-pl` 无法解析依赖，需用 `-am` 一并纳入 reactor：

```bash
mvn test -Dtest=ConstantConfigIntegrationTest -pl yudao-server -am \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- `-am`：同时构建 `yudao-server` 依赖的兄弟模块。
- `-Dsurefire.failIfNoSpecifiedTests=false`：避免 `-Dtest` 指定到无该测试类的其它模块时报错。

### 5. 集成测试示例（@SpringBootTest）

引入依赖并 `@Autowired` 门面 Bean 即可直接测试，无需任何额外配置：

```java
@SpringBootTest(classes = YudaoServerApplication.class)
class ConstantConfigIntegrationTest {

    @Autowired
    private ConstantConfigCenter ccc;

    @Test
    void createAndReadConfig() {
        ConstantConfig item = new ConstantConfig();
        item.setConfigName("比功率有效区间");
        item.setKey("iot.power.range");
        item.setValue("[1,50]");
        item.setValueType(ConstantConfigValueType.STRING);
        // pending...
    }
}
```

### 踩坑清单

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| `Could not find artifact yudao-module-system:jar` | 单模块 `-pl` 不含依赖的本地模块 | 加 `-am` |
| `找不到符号 YudaoServerApplication` | 测试类 import 包名与启动类不符（启动类在 `.server` 子包） | 用 `import cn.iocoder.yudao.server.YudaoServerApplication;` |
| `Unknown lifecycle phase .failIfNoSpecifiedTests=false` | 某些 shell 拆分 `-D` 参数带点号 | 给参数加引号 `"-Dsurefire.failIfNoSpecifiedTests=false"` |
| `父分类不存在 categoryId=1` | 建表时漏执行默认分类 INSERT | 补插 `category_id=1` 默认分类 |
| `Table 'xxx.constant_config_center' doesn't exist` | 未执行建表脚本 | 执行 [constant_config_center.sql](sql/constant_config_center.sql) |

---

## 变更记录

### v2.3 —— 命名规约对齐 + 方言抽象 + 响应字段收敛（内部重构）

> 内部重构，**不改变任何对外 SPI / 门面行为**；其中 A3/D1/E 沿用既有「整体替换」装配契约，回归 33 项全绿（缓存 5 + 集成 28）。

**1. DT 层命名规约对齐 yudao（后缀规约）**
- 实体：`ConstantConfigDO` / `ConstantConfigCategoryDO`（DO 后缀，保持）。
- 查询 / 保存入参：`XxxReqVO` —— `ConfigPageReqVO`、`CategoryPageReqVO`、`ConfigSaveReqVO`、`CategorySaveReqVO`。
- 响应：`XxxRespVO` —— `ConfigRespVO`、`CategoryRespVO`。
- 旧 `SaveConfigCommand` / `ConfigView` / `ConfigPageQuery` 等类名及 README / 方案文档 / SVG 中的引用已全局同步清理。

**2. D1 跨库方言抽象**
- 新增 `SqlDialect` 接口（时间戳表达式 + 分页占位 + 单条取数）与 `MysqlDialect` / `H2Dialect` 两个默认实现、及按 `DatabaseMetaData` 探测的 `SqlDialects.detect`。
- 两个 JDBC Provider 的 `NOW()`、`LIMIT ? OFFSET ?`、`LIMIT 1` 全部改由方言提供；装配类注册 `SqlDialect` Bean（`@ConditionalOnMissingBean`，可被对接方覆盖）。B8 已消除反引号，本批收敛剩余方言点。

**3. E 响应字段收敛 + 映射去重**
- `ConfigRespVO` 移除存储侧 `version` / `createTime` / `updateTime`（对齐 B6「响应剥掉存储字段」）；`updateConfig` 的乐观锁版本省略时由门面按当前快照版本兜底，行为不变。
- 门面抽取 `mapList(List<S>, Function<S,T>)`，去重 `getConfigList` / `getConfigPage` / `getCategoryList` / `getCategoryPage` / `listCategoryTree` 5 处「循环 + add + 组装」样板。

**4. A3 整体替换装配取舍说明**
- 默认 JDBC Provider 读/写绑定为单一 Bean，以 `@ConditionalOnMissingBean(ReadStore)` 抑制。取舍边界已写入装配类 javadoc：仅替换「读」一侧会连带失去默认「写」侧，需同时补全写侧 Bean，否则门面装配失败；统一定案走「整体替换」避免读/写独立装配的注入歧义。

---

### v2.2 —— 校验收敛 + 缓存防御性拷贝 + 删除原子化（内部重构）

> 内部重构，**不改变任何对外 SPI / 门面签名**，回归 33 项全绿（缓存 5 + 集成 28）。

**1. 门面层校验收敛（A2）**
- `createConfig`：新增 `key` / `configName` 非空非白、`value` 非空断言；解析出的 `categoryId` 与显式传入的分类做「存在性」校验。
- `updateConfig`：`key` 非空断言；显式给出的新 `configName` 非空校验；显式迁移到新分类时校验其存在。
- 效果：把原本落到 DB 的裸 `NOT NULL` / 外键异常，前移为 `ConstantConfigException` 语义异常。

**2. 缓存防御性拷贝（B1）**
- `ConstantConfigCache` 的 `put*` 改为先逐字段拷贝 DO / 列表再入缓存，杜绝自定义 Provider 共享可变对象被外部篡改污染缓存；读取路径不加拷贝、不增热路径开销。

**3. 删除分类原子化（B2）**
- `deleteCategory` 去掉 `countByCategory` 预读；分类下配置的存在性改由外键 `fk_ccc_category_id(ON DELETE RESTRICT)` 在 DELETE 时原子拦截，`DataIntegrityViolationException → ConstantConfigException`。
- 子分类预检保留（`category_parent_id` 无自引用外键，无法靠 FK 兜底，残余竞态极小）。

---

### v2.1 —— 职责收窄：补丁合并上移门面 + 分页默认值收敛（内部重构）

> 内部重构，**不改变任何对外 SPI / 门面签名与行为**，回归 27 项全绿。

**1. `updateConfig` 补丁合并规则上移门面**
- 原实现：`config_name` / `categoryId` / `value` / `valueType` / `remark` 的「null 保留原值」合并逻辑写在 JDBC Provider（存储层感知写业务规则），且每次更新先读后写（2 查）。
- 现实现：门面先读当前快照，完成「非空覆盖合并」后以**完整快照**交给写 SPI；存储层单条 `UPDATE ... WHERE config_key=? AND version=?` 直更 + 版本 CAS。`config_name` 冲突改由 DB 唯一键 `uk_config_name` 拦截并翻译为 `ConstantConfigConflictException`。
- 行为保持：更新改值+版本自增、过期版本抛 `ConstantConfigVersionMismatchException`、缺失抛 `NotFoundException`、改名冲突均不变。

**2. 分页默认值收敛 `size`**
- `ConfigPageReqVO` / `CategoryPageReqVO` 的 `size` 默认由硬编码 `10` 改为 `0`（未设置），由门面 `Pagination.of` 统一按 `spring.constant-config-center.default-page-size`（默认 10）兜底，消除「属性默认 vs query 默认」双漂移点。

---

### v1.1 —— 列名去保留字（B8，破坏性）

> 数据库列 `key` / `value` 改为 `config_key` / `config_value`，去掉 MySQL 保留字、不再依赖反引号包裹，提升跨库方言兼容性；SQL 唯一键调整为 `uk_config_key`。

**兼容性影响**：属**破坏性变更**，已部署的存量库不能靠 `CREATE TABLE IF NOT EXISTS` 增量升级。

- 全新部署：直接执行 `sql/constant_config_center.sql`（新版）。
- 存量升级（如 yudao 集成库）：执行 `sql/migrate_rename_key_value.sql`（重建 + 迁移数据），并注意：
  - 执行前**备份**，建议停写窗口；
  - 需按脚本内 **Step 3 核对新旧表行数一致**后再执行 Step 4 的替换。
- 程序层 `key`（业务键）与门面 API 不变，仅存储列名变化。

---

### v2.0 —— 读侧缓存 + 变更事件（B9）

> 增强项：读加内存缓存、写发变更事件，均为内部增强，**不改变任何对外 SPI / 门面签名**。

**1. 读侧自研 TTL 内存缓存（`cache` 包）**
- 缓存范围：`getConfig`（含默认值 / 类型化读取）、`getKeyByConfigName`、`listCategoryTree`；列表 / 分页刻意不缓存（失效粒度粗、易脏读）。
- `ConstantConfigCache`：`ConcurrentHashMap` + 惰性过期清理、容量超限停止写入回源 DB、不做负缓存；`@ConditionalOnMissingBean` 可整体替换。
- 新增配置 `cache-enabled`（默认 true）/ `cache-ttl-seconds`（300）/ `cache-max-size`（1000）；`cache-enabled=false` 时门面退化为直读 DB。

**2. 变更事件（`event` 包）**
- `ConfigChangeType`（`CREATED`/`UPDATED`/`DELETED`）+ `ConfigChangedEvent` + `CategoryChangedEvent`，写成功后由门面发布。
- `CacheInvalidationListener` 本地消费同一事件失效缓存：配置**更新**整体失效双索引，`CREATED`/`DELETED` 按 key（+ 回填名称）精确失效；分类变更整体失效树。

**3. 一致性策略**
- 事件 ± TTL 双保险：本机写→事件即时失效；跨实例 / 直插 DB→TTL 兜底收敛脏读窗口。
- `deleteConfig` 先回读 `config_name` 供失效「按名称反查 key」索引。

**回归**：新增 `ConstantConfigCacheTest`（4 项），连同既有集成测试共 27 项全绿。

---

### v1.0.1 —— 三项 P0 数据一致性修复

> 均为 **正确性 / 健壮性** 修复，不含功能扩展。若沿用旧的 `ConstantConfigProvider` 自定义实现，请同步补方法。涉及 DDL 时遵循下方注明。

**1. `deleteCategory` 增加"分类下无配置"校验（应用层保护）**
- 原行为：删除分类只校验"无子分类"，分类下若已挂配置仍可删除，导致配置变为孤儿（`category_id` 悬空）。
- 现行为：校验链 = 分类存在 → 无子分类 → **分类下无配置** → 删除；分类下有配置抛 `ConstantConfigException`，提示先清空或迁移。
- 变更：`ConstantConfigProvider` 新增 `long countByCategory(Long categoryId)`。

**2. `updateConfig` 落实 version 乐观锁（CAS）**
- 原行为：`UPDATE` 仅 `WHERE key=?` 且 `version=version+1`，无版本比对，并发更新互相覆盖。
- 现行为：`UPDATE` 带 `WHERE key=? AND version=?`；提交的期望版本与库里当前版本不一致则抛新增的
  `ConstantConfigVersionMismatchException`（携带 `key` / 期望版本 / 实际版本）。
- 兼容策略：`item.version` 为空时，以读取到的当前版本作保底 CAS，旧调用方式不受影响，同时堵住"读旧值→更新"窗口内的覆盖竞态。

**3. `constant_config_center.category_id` 增加外键兜底（DB 层保护）**
- 建表脚本 [constant_config_center.sql](sql/constant_config_center.sql) 已内联：
  `FOREIGN KEY (category_id) REFERENCES constant_config_category(category_id) ON UPDATE RESTRICT ON DELETE RESTRICT`。
- 存量库升级：执行 [migrate_add_category_fk.sql](sql/migrate_add_category_fk.sql)，Step1 先清理孤儿配置，Step2 再加外键。
- 策略说明：采用 **`RESTRICT`**（删除仍有配置的分类时被 DB 拒绝）而非 `CASCADE`（连带删除配置），
  与应用层"删分类需无配置"语义一致，配置不会因删分类被静默连带删除。

**回归**：新增 3 个集成用例（删分类下有配置被拒 / 过期版本提交被乐观锁拦截 / 直插孤儿配置被外键拦截），starter `mvn test` 全量通过（23 项）。
