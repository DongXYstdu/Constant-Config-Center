package com.constantconfig.center.spi;

import com.constantconfig.center.model.entity.ConstantConfigDO;
import com.constantconfig.center.exception.ConstantConfigConflictException;
import com.constantconfig.center.exception.ConstantConfigVersionMismatchException;

/**
 * 常量配置写入侧存储 SPI（写扩展点）
 *
 * <p>与 {@link ConfigReadStore} 分离，使自定义后端可按需只实现「写」或「读」一侧。
 * {@code value} 为文本形态（LIST / MAP 的 JSON 由门面层序列化），本层不感知值类型语义。</p>
 *
 * @see ConfigReadStore
 */
public interface ConfigWriteStore {

    /**
     * 新增配置条目（纯新增，返回主键 id）
     *
     * <p>{@code id} 无需设置，由存储层维护；{@code version} 初始为 0，时间列取当前时间。</p>
     *
     * @param item 配置条目
     * @return 新记录主键 id
     * @throws ConstantConfigConflictException {@code config_name} 或 {@code key} 任一唯一键
     *         已被其它记录占用时抛出，可通过 {@link ConstantConfigConflictException#getExistingId()}
     *         获取已存在行的主键 id
     */
    Long create(ConstantConfigDO item);

    /**
     * 更新配置条目（按 {@code key} 定位，直更密钥相关字段）
     *
     * <p>输入为<b>门面已合并好的完整快照</b>（{@code categoryId} / {@code configName} / {@code value} /
     * {@code valueType} / {@code remark} 均已填充最终值，不做「null 保留原值」的补丁合并）；
     * 本层不修改 {@code key} 本身，{@code version} 自增并刷新 {@code updateTime}。</p>
     *
     * <p>乐观并发：{@code item.version} 作为期望版本参与 CAS，与实际版本不一致则抛
     * {@link ConstantConfigVersionMismatchException}。{@code config_name} 与其它记录冲突时抛
     * {@link ConstantConfigConflictException}（由 DB 唯一键拦截并翻译）。</p>
     *
     * @param item 待写入的完整快照（必须携带 {@code key}；{@code version} 为期望版本）
     * @return 目标记录是否存在（不存在或已被删除返回 {@code false}，由门面转抛异常）
     * @throws ConstantConfigConflictException 新的 {@code config_name} 被其它记录占用时抛出
     * @throws ConstantConfigVersionMismatchException 版本不一致（并发修改）时抛出
     */
    boolean update(ConstantConfigDO item);

    /**
     * 删除配置条目（按 {@code key} 定位）
     *
     * @param key 键
     * @return 是否删除成功（目标不存在返回 {@code false}，由门面转抛异常）
     */
    boolean delete(String key);
}