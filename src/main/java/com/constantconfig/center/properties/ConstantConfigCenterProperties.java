package com.constantconfig.center.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 常量配置中心属性配置
 *
 * <p>通过 {@code spring.constant-config-center.*} 前缀绑定外部配置。</p>
 */
@ConfigurationProperties(prefix = "spring.constant-config-center")
public class ConstantConfigCenterProperties {

    /** 是否启用常量配置中心 */
    private boolean enabled = true;

    /** 存储表名，可自定义 */
    private String table = "constant_config_center";

    /** 分类表名，可自定义 */
    private String categoryTable = "constant_config_category";

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
}
