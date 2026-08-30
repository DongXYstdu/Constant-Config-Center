-- =============================================================
-- 迁移脚本：v1.0 存量 constant_config_center 表 -> B8 新结构（重建 + 迁移数据）
-- B8 说明：key/value 是 MySQL 保留字，已改名为 config_key/config_value，故需重建表迁移数据
-- 适用：已按 v1.0.0 建表、存在存量数据、且无法用 CREATE TABLE IF NOT EXISTS 增量升级的对接方
--       （典型：yudao 集成库）
-- 前置：constant_config_category 已存在且含默认分类 id=1；表名保持默认 constant_config_center
-- 注意：DDL 语句在 MySQL 中隐式提交、非事务，执行前务必备份库，建议在停写窗口执行
--       执行到 Step 3 后请人工核对两个表的行数一致，再继续 Step 4 的替换（见文末说明）
-- =============================================================

-- -------------------------------------------------------------
-- Step 1: 用 B8 新结构（config_key/config_value）创建临时新表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `constant_config_center_new` (
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
    CONSTRAINT `fk_ccc_category_id` FOREIGN KEY (`category_id`)
        REFERENCES `constant_config_category` (`category_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常量配置中心表（B8 去保留字）';

-- -------------------------------------------------------------
-- Step 2: 迁移数据（key -> config_key，value -> config_value，其余列一一对应）
-- -------------------------------------------------------------
INSERT INTO `constant_config_center_new`
    (`id`, `category_id`, `config_name`, `config_key`, `config_value`, `value_type`, `version`, `remark`, `create_time`, `update_time`)
SELECT
    `id`, `category_id`, `config_name`, `key`, `value`, `value_type`, `version`, `remark`, `create_time`, `update_time`
FROM `constant_config_center`;

-- -------------------------------------------------------------
-- Step 3: 行数核对（******************** 请人工确认 ********************）
--         下面两个计数必须一致；不一致请立即回滚，勿继续
-- -------------------------------------------------------------
SELECT 'OLD' AS `schema`, COUNT(*) AS `cnt` FROM `constant_config_center`;
SELECT 'NEW' AS `schema`, COUNT(*) AS `cnt` FROM `constant_config_center_new`;

-- -------------------------------------------------------------
-- Step 4: 替换（仅在 Step 3 确认一致后执行；已确认一致可取消下方注释逐行运行）
-- -------------------------------------------------------------
-- DROP TABLE `constant_config_center`;
-- ALTER TABLE `constant_config_center_new` RENAME TO `constant_config_center`;

-- -------------------------------------------------------------
-- Step 5: 校验（替换后执行）
-- -------------------------------------------------------------
-- SELECT `id`, `category_id`, `config_key`, `config_value`
-- FROM `constant_config_center` ORDER BY `id` LIMIT 5;
--
-- -- 孤儿校验：应返回空集
-- SELECT `id`, `category_id` FROM `constant_config_center`
-- WHERE `category_id` NOT IN (SELECT `category_id` FROM `constant_config_category`);