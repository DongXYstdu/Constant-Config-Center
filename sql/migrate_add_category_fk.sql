-- =============================================================
-- 迁移脚本：为已存量的 constant_config_center 表补充 category_id 外键约束
-- 适用：已按 v1.0.0 建表、存在存量数据、需升级到「带外键约束」schema 的对接方
-- 与全新部署的区别：全新库直接用 constant_config_center.sql（已内联外键），无需执行本脚本
-- 注意：执行前请先备份数据
-- =============================================================

-- Step 1: 清理孤儿配置（category_id 在分类表中已不存在的历史脏数据）
--        删掉的是“分类被删后残留的悬空配置”，配合 v1.0.x 应用层「删分类需无配置」校验，
--        存量清理后，后续应用层拦截 + 外键兜底双保险，不再产生新孤儿。
DELETE FROM `constant_config_center`
WHERE `category_id` NOT IN (SELECT `category_id` FROM `constant_config_category`);

-- Step 2: 补充外键约束（应用层删除分类只能删“无配置无子分类”的分类，故 RESTRICT 行为安全）
ALTER TABLE `constant_config_center`
    ADD CONSTRAINT `fk_ccc_category_id`
    FOREIGN KEY (`category_id`) REFERENCES `constant_config_category` (`category_id`)
    ON UPDATE RESTRICT ON DELETE RESTRICT;

-- 校验：查询应返回空集；若有行，说明外键未生效或存在数据问题
SELECT `id`, `category_id`, `key`
FROM `constant_config_center`
WHERE `category_id` NOT IN (SELECT `category_id` FROM `constant_config_category`);