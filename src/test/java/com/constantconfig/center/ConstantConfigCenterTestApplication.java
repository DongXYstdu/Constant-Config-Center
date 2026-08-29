package com.constantconfig.center;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 测试引导类
 *
 * <p>本项目为 starter，无独立启动入口；测试通过本类提供
 * {@link SpringBootConfiguration} + {@link EnableAutoConfiguration}，
 * 由自动装配（AutoConfiguration.imports 注册的
 * {@link ConstantConfigCenterAutoConfiguration}）装配 H2 + JDBC + 门面全链路。</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class ConstantConfigCenterTestApplication {
}
