package com.constantconfig.center;

import com.constantconfig.center.api.ConstantConfigCenter;
import com.constantconfig.center.core.ConstantConfigCenterCategoryProvider;
import com.constantconfig.center.core.ConstantConfigCenterImpl;
import com.constantconfig.center.core.ConstantConfigCenterProvider;
import com.constantconfig.center.provider.ConstantConfigCenterCategoryJdbcProvider;
import com.constantconfig.center.provider.ConstantConfigCenterJdbcProvider;
import com.constantconfig.center.properties.ConstantConfigCenterProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 常量配置中心自动装配入口
 *
 * <p>V0.2：装配属性类；V0.3：注册 JDBC 默认存储实现 Provider；V0.4：注册门面；
 * V0.6：注册树形分类 Provider。</p>
 *
 * <ul>
 *   <li>通过 {@code spring.constant-config-center.enabled} 控制是否启用（默认启用）。</li>
 *   <li>仅当容器中存在 {@link JdbcTemplate} 时注册 JDBC Provider（配置 + 分类）。</li>
 *   <li>对接方已自定义 {@link ConstantConfigCenterProvider} / {@link ConstantConfigCenterCategoryProvider} Bean 时，跳过默认实现（可整体替换）。</li>
 *   <li>对接方已自定义 {@link ConstantConfigCenter} Bean 时，跳过默认门面实现。</li>
 *   <li>容器中无 {@link ObjectMapper} 时提供兜底实例，保证开箱即用。</li>
 * </ul>
 */
@AutoConfiguration
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
@EnableConfigurationProperties(ConstantConfigCenterProperties.class)
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "spring.constant-config-center", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConstantConfigCenterAutoConfiguration {

    /**
     * 注册 JDBC 默认存储实现（配置键值表）
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(ConstantConfigCenterProvider.class)
    public ConstantConfigCenterProvider constantConfigCenterProvider(JdbcTemplate jdbcTemplate,
                                                                     ConstantConfigCenterProperties properties) {
        return new ConstantConfigCenterJdbcProvider(jdbcTemplate, properties);
    }

    /**
     * 注册 JDBC 默认分类存储实现（树形分类表）
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(ConstantConfigCenterCategoryProvider.class)
    public ConstantConfigCenterCategoryProvider constantConfigCenterCategoryProvider(JdbcTemplate jdbcTemplate,
                                                                                     ConstantConfigCenterProperties properties) {
        return new ConstantConfigCenterCategoryJdbcProvider(jdbcTemplate, properties);
    }

    /**
     * 容器无 ObjectMapper 时提供兜底实例（LIST / MAP 反序列化用）
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper constantConfigCenterObjectMapper() {
        return new ObjectMapper();
    }

    /**
     * 注册门面默认实现
     */
    @Bean
    @ConditionalOnMissingBean(ConstantConfigCenter.class)
    public ConstantConfigCenter constantConfigCenter(ConstantConfigCenterProvider provider,
                                                     ConstantConfigCenterCategoryProvider categoryProvider,
                                                     ObjectMapper objectMapper) {
        return new ConstantConfigCenterImpl(provider, categoryProvider, objectMapper);
    }
}
