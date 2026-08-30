-- =============================================================
-- 测试建表脚本（H2 MySQL 兼容模式）
-- 结构对齐 production constant_config_center.sql：
--   constant_config_category（树形分类表）+ constant_config_center（配置键值表）
-- 保留 config_name / key 全局唯一键，用于验证唯一键冲突异常
-- =============================================================

-- 1. 配置分类表
CREATE TABLE IF NOT EXISTS constant_config_category (
    category_id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_parent_id  BIGINT       NOT NULL DEFAULT 0,
    category_name       VARCHAR(64)  NOT NULL,
    path                VARCHAR(512) NOT NULL,
    level               INT          NOT NULL DEFAULT 1,
    sort                INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_category_name UNIQUE (category_name)
);

-- 默认分类（id=1，业务上未显式指定分类时使用）
INSERT INTO constant_config_category (category_id, category_parent_id, category_name, path, level, sort)
VALUES (1, 0, '默认', '/1', 1, 0);

-- 2. 配置键值表（config_name 与 config_key 均全局唯一；B8 已去保留字）
CREATE TABLE IF NOT EXISTS constant_config_center (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_id   BIGINT        NOT NULL,
    config_name   VARCHAR(128)  NOT NULL,
    config_key    VARCHAR(128)  NOT NULL,
    config_value  VARCHAR(1000) NOT NULL,
    value_type    VARCHAR(16)   NOT NULL DEFAULT 'STRING',
    version       BIGINT        NOT NULL DEFAULT 0,
    remark        VARCHAR(255)  DEFAULT NULL,
    create_time   TIMESTAMP     NOT NULL,
    update_time   TIMESTAMP     NOT NULL,
    CONSTRAINT uk_config_name UNIQUE (config_name),
    CONSTRAINT uk_config_key UNIQUE (config_key),
    -- 外键兜底（对齐 production 外键约束）：防止 category_id 指向不存在的分类
    CONSTRAINT fk_ccc_category_id FOREIGN KEY (category_id)
        REFERENCES constant_config_category (category_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);
