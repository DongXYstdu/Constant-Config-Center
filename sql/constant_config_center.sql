-- =============================================================
-- 常量配置中心建表脚本
-- 说明：由对接方初始化执行；表名可通过 spring.constant-config-center.table 自定义
-- 结构：constant_config_category（树形分类表） + constant_config_center（配置键值表）
-- =============================================================

-- -------------------------------------------------------------
-- 1. 配置分类表
-- 结构：邻接表（parent_id）+ 物化路径（path）冗余，便于子树查询
-- 默认分类：请先插入 category_id=1 的根分类（名称“默认”）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `constant_config_category` (
    `category_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `category_parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类ID，0 表示根节点',
    `category_name` VARCHAR(64)  NOT NULL COMMENT '分类中文名称（唯一，用于去重）',
    `path`        VARCHAR(512) NOT NULL COMMENT '层级路径，如 /1/2/3，根节点为 /1',
    `level`       INT          NOT NULL DEFAULT 1 COMMENT '层级深度，根为 1',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '同级排序号',
    PRIMARY KEY (`category_id`),
    UNIQUE KEY `uk_category_name` (`category_name`),
    KEY `idx_parent_id` (`category_parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常量配置分类表';

-- 默认分类（id=1），业务上未显式指定分类时使用
INSERT INTO `constant_config_category`
    (`category_id`, `category_parent_id`, `category_name`, `path`, `level`, `sort`)
VALUES
    (1, 0, '默认', '/1', 1, 0);

-- -------------------------------------------------------------
-- 2. 配置键值表
-- 说明：category_id 关联 constant_config_category.category_id
--       config_name（常量配置名称）与 config_key（键）均全局唯一，互不重复
-- B8：key/value 改名为 config_key/config_value，去掉 MySQL 保留字，避免反引号包裹
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `constant_config_center` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_id` BIGINT       NOT NULL COMMENT '分类ID（关联 constant_config_category.category_id）',
    `config_name`  VARCHAR(128) NOT NULL COMMENT '常量配置名称（全局唯一）',
    `config_key`   VARCHAR(128) NOT NULL COMMENT '键（全局唯一，程序取值用）',
    `config_value` TEXT         NOT NULL COMMENT '值（当前为 String；LIST/MAP 场景存 JSON 文本）',
    `value_type`  VARCHAR(16)  NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING | LIST | MAP',
    `version`     BIGINT       NOT NULL DEFAULT 0 COMMENT '版本号，用于乐观并发与变更识别',
    `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME     NOT NULL COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_name` (`config_name`),
    UNIQUE KEY `uk_config_key` (`config_key`),
    -- 外键兜底：防止 category_id 指向不存在的分类（配合应用层“删分类需无配置”校验双重防护）
    CONSTRAINT `fk_ccc_category_id` FOREIGN KEY (`category_id`)
        REFERENCES `constant_config_category` (`category_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常量配置中心表';
