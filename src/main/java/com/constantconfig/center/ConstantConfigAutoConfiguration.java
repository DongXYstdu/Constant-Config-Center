package com.constantconfig.center;

import com.constantconfig.center.api.ConstantConfigCenter;
import com.constantconfig.center.cache.CacheInvalidationListener;
import com.constantconfig.center.cache.ConstantConfigCache;
import com.constantconfig.center.model.codec.ValueCodec;
import com.constantconfig.center.service.ConstantConfigCenterImpl;
import com.constantconfig.center.spi.CategoryReadStore;
import com.constantconfig.center.spi.CategoryWriteStore;
import com.constantconfig.center.spi.ConfigReadStore;
import com.constantconfig.center.spi.ConfigWriteStore;
import com.constantconfig.center.spi.jdbc.ConstantConfigCategoryJdbcProvider;
import com.constantconfig.center.spi.jdbc.ConstantConfigJdbcProvider;
import com.constantconfig.center.properties.ConstantConfigProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 常量配置中心自动装配入口
 *
 * <p>V0.2：装配属性类；V0.3：注册 JDBC 默认存储实现 Provider；V0.4：注册门面；
 * V0.6：注册树形分类 Provider；V0.8：按分包规则重构（model/spi/service/query 分离）。</p>
 *
 * <ul>
 *   <li>通过 {@code spring.constant-config-center.enabled} 控制是否启用（默认启用）。</li>
 *   <li>仅当容器中存在 {@link JdbcTemplate} 时注册 JDBC 默认存储（配置 + 分类）。</li>
 *   <li>读写 SPI 已按 CQRS 拆分为读/写两组接口；默认 JDBC 实现类同时提供读+写，
 *       以单一 Bean 注册并以「整体替换」对外暴露（对接方自定义读或写一侧 Bean 时，
 *       默认 JDBC 存储被抑制，可整体替换默认实现）。</li>
 *   <li>对接方已自定义 {@link ConstantConfigCenter} Bean 时，跳过默认门面实现。</li>
 *   <li>容器中无 {@link ObjectMapper} 时提供兜底实例，保证开箱即用。</li>
 * </ul>
 */
@AutoConfiguration
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
@EnableConfigurationProperties(ConstantConfigProperties.class)
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "spring.constant-config-center", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConstantConfigAutoConfiguration {

    /**
     * 注册 JDBC 默认配置存储（键值表）：同时实现 {@link ConfigReadStore} + {@link ConfigWriteStore}，
     * 以单一 Bean 提供读写两侧；对接方自定义 ConfigReadStore 时（整体替换）本默认 Bean 被抑制。
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(ConfigReadStore.class)
    public ConstantConfigJdbcProvider constantConfigJdbcProvider(JdbcTemplate jdbcTemplate,
                                                                 ConstantConfigProperties properties) {
        return new ConstantConfigJdbcProvider(jdbcTemplate, properties);
    }

    /**
     * 注册 JDBC 默认分类存储（树形分类表）：同时实现 {@link CategoryReadStore} +
     * {@link CategoryWriteStore}，以单一 Bean 提供读写两侧；对接方自定义 CategoryReadStore
     * 时（整体替换）本默认 Bean 被抑制。
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(CategoryReadStore.class)
    public ConstantConfigCategoryJdbcProvider constantConfigCategoryJdbcProvider(
            JdbcTemplate jdbcTemplate,
            ConstantConfigProperties properties,
            TransactionTemplate transactionTemplate) {
        return new ConstantConfigCategoryJdbcProvider(jdbcTemplate, properties, transactionTemplate);
    }

    /**
     * 容器无 ObjectMapper 时提供兜底实例（LIST / MAP 反序列化用）
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper constantConfigObjectMapper() {
        return new ObjectMapper();
    }

    /**
     * 注册值编解码器（基于 ObjectMapper，处理 LIST / MAP 的序列化与反序列化）
     */
    @Bean
    @ConditionalOnMissingBean(ValueCodec.class)
    public ValueCodec valueCodec(ObjectMapper objectMapper) {
        return new ValueCodec(objectMapper);
    }

    /**
     * 注册读侧内存缓存（自研 TTL）；{@code cache-enabled} 为 false 时不注册，
     * 门面（{@link ObjectProvider} 取空）退化为直读 DB。
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.constant-config-center", name = "cache-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(ConstantConfigCache.class)
    public ConstantConfigCache constantConfigCache(ConstantConfigProperties properties) {
        return new ConstantConfigCache(properties.getCacheTtlSeconds(), properties.getCacheMaxSize());
    }

    /**
     * 注册本地缓存失效监听：消费门面发布的变更事件，按需失效缓存索引。
     */
    @Bean
    @ConditionalOnBean(ConstantConfigCache.class)
    @ConditionalOnMissingBean(CacheInvalidationListener.class)
    public CacheInvalidationListener cacheInvalidationListener(ConstantConfigCache cache) {
        return new CacheInvalidationListener(cache);
    }

    /**
     * 注册门面默认实现
     */
    @Bean
    @ConditionalOnMissingBean(ConstantConfigCenter.class)
    public ConstantConfigCenter constantConfigCenter(ConfigReadStore configRead,
                                                     ConfigWriteStore configWrite,
                                                     CategoryReadStore categoryRead,
                                                     CategoryWriteStore categoryWrite,
                                                     ValueCodec valueCodec,
                                                     ConstantConfigProperties properties,
                                                     ObjectProvider<ConstantConfigCache> cacheProvider,
                                                     ApplicationEventPublisher eventPublisher) {
        return new ConstantConfigCenterImpl(
                configRead, configWrite, categoryRead, categoryWrite, valueCodec, properties, cacheProvider, eventPublisher);
    }
}