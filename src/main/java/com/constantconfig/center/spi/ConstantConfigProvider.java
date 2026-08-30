package com.constantconfig.center.spi;

import com.constantconfig.center.model.ConstantConfig;
import com.constantconfig.center.exception.ConstantConfigConflictException;

import java.util.List;

/**
 * 常量配置存储后端 SPI（扩展点）
 *
 * <p>其它项目可自行实现本接口并注册为 Spring Bean，替换或叠加默认的 JDBC 存储后端。</p>
 *
 * <p><b>职责边界</b>：本接口只负责配置数据的存储读写，返回 {@link ConstantConfig} 模型；
 * 值类型转换（如 LIST / MAP 的 JSON 反序列化）由上层门面 {@code ConstantConfigCenter} 负责。</p>
 *
 * <p><b>原语约定</b>：{@code key} 全局唯一，是读写删的定位键；{@code update} / {@code delete}
 * 返回 {@code boolean} 表示目标记录是否存在（不存在返回 {@code false}，由门面转抛业务异常）；
 * {@code create} 遇 {@code config_name} 或 {@code key} 唯一键冲突时抛
 * {@link ConstantConfigConflictException}。</p>
 */
public interface ConstantConfigProvider {

    /**
     * 按键查询配置条目（{@code key} 全局唯一）
     *
     * @param key 键
     * @return 配置条目；不存在时返回 {@code null}
     */
    ConstantConfig get(String key);

    /**
     * 按常量配置名称查询（{@code config_name} 全局唯一，用于名称反查与去重）
     *
     * @param configName 常量配置名称
     * @return 配置条目；不存在时返回 {@code null}
     */
    ConstantConfig getByConfigName(String configName);

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
    Long create(ConstantConfig item);

    /**
     * 更新配置条目（按 {@code key} 定位，更新值相关字段）
     *
     * <p>可更新 {@code configName} / {@code value} / {@code valueType} / {@code remark} /
     * {@code categoryId}，不修改 {@code key} 本身；{@code version} 自增并刷新 {@code updateTime}。</p>
     *
     * <p>乐观并发：{@code item.version} 非空时作为期望版本参与 CAS（{@code WHERE key AND version}），
     * 与实际版本不一致则抛 {@link ConstantConfigVersionMismatchException}；为空时以已读取的当前版本
     * 作保底校验，同样可防「读旧值再更新」窗口内的覆盖竞态。</p>
     *
     * @param item 配置条目（必须携带 {@code key}）
     * @return 目标记录是否存在（不存在返回 {@code false}，由门面转抛异常）
     * @throws ConstantConfigConflictException 新的 {@code config_name} 被其它记录占用时抛出
     * @throws ConstantConfigVersionMismatchException 版本不一致（并发修改）时抛出
     */
    boolean update(ConstantConfig item);

    /**
     * 删除配置条目（按 {@code key} 定位）
     *
     * @param key 键
     * @return 是否删除成功（目标不存在返回 {@code false}，由门面转抛异常）
     */
    boolean delete(String key);

    /**
     * 配置列表（可选按分类 + 关键字过滤，命中全部）
     *
     * @param categoryId 分类ID；{@code null} 查询全部
     * @param keyword 关键字，模糊匹配 key / config_name；{@code null} / 空则不过滤
     * @return 配置条目列表；无数据时返回空列表（非 null）
     */
    List<ConstantConfig> list(Long categoryId, String keyword);

    /**
     * 配置分页查询（按下标范围返回命中记录的某一页）
     *
     * @param categoryId 分类ID；{@code null} 查询全部
     * @param keyword 关键字，模糊匹配 key / config_name；{@code null} / 空则不过滤
     * @param offset 起始行偏移（从 0 开始）
     * @param limit 返回行数上限
     * @return 该页配置条目列表；无数据时返回空列表（非 null）
     */
    List<ConstantConfig> listPage(Long categoryId, String keyword, int offset, int limit);

    /**
     * 按过滤条件统计配置条目的命中总数
     *
     * @param categoryId 分类ID；{@code null} 查询全部
     * @param keyword 关键字，模糊匹配 key / config_name；{@code null} / 空则不过滤
     * @return 命中总数
     */
    long count(Long categoryId, String keyword);

    /**
     * 统计某个分类下挂载的配置条数（用于删除分类前的完整性校验）
     *
     * @param categoryId 分类ID
     * @return 该分类下的配置条数（0 表示可安全删除）
     */
    long countByCategory(Long categoryId);
}