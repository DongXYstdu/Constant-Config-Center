package com.constantconfig.center.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 常量配置中心属性配置
 *
 * <p>通过 {@code spring.constant-config-center.*} 前缀绑定外部配置。</p>
 */
@ConfigurationProperties(prefix = "spring.constant-config-center")
public class ConstantConfigProperties {

    /** 是否启用常量配置中心 */
    private boolean enabled = true;

    /** 存储表名，可自定义 */
    private String table = "constant_config_center";

    /** 分类表名，可自定义 */
    private String categoryTable = "constant_config_category";

    /** 默认分类 ID（未显式指定分类时使用），默认 1 */
    private Long defaultCategoryId = 1L;

    /** 默认每页大小（分页查询未指定时使用），默认 10 */
    private int defaultPageSize = 10;

    /** 新建记录时初始版本号，默认 0 */
    private long defaultVersion = 0L;

    /** 是否启用读侧内存缓存（默认启用） */
    private boolean cacheEnabled = true;

    /** 读缓存 TTL 秒数，默认 300 */
    private long cacheTtlSeconds = 300L;

    /** 读缓存容量上限（>0 生效，超出时停止写入新缓存），默认 1000 */
    private int cacheMaxSize = 1000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getCategoryTable() {
        return categoryTable;
    }

    public void setCategoryTable(String categoryTable) {
        this.categoryTable = categoryTable;
    }

    public Long getDefaultCategoryId() {
        return defaultCategoryId;
    }

    public void setDefaultCategoryId(Long defaultCategoryId) {
        this.defaultCategoryId = defaultCategoryId;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public long getDefaultVersion() {
        return defaultVersion;
    }

    public void setDefaultVersion(long defaultVersion) {
        this.defaultVersion = defaultVersion;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }
}