package com.constantconfig.center.exception;

/**
 * 常量配置版本冲突异常（乐观并发冲突）
 *
 * <p>更新配置时启用乐观锁校验，若提交的期望版本号与数据库当前版本号不一致，
 * 说明数据已被其它并发更新修改，本次更新失败并抛出本异常。通过
 * {@link #getExpectedVersion()} / {@link #getActualVersion()} 可分别拿到客户端
 * 提交的期望版本与数据库当前实际版本，便于调用方重读最新值后重试。</p>
 */
public class ConstantConfigVersionMismatchException extends ConstantConfigException {

    /** 定位的 key */
    private final String key;

    /** 客户端提交的期望版本号 */
    private final Long expectedVersion;

    /** 数据库当前实际版本号 */
    private final Long actualVersion;

    public ConstantConfigVersionMismatchException(String key, Long expectedVersion, Long actualVersion) {
        super("常量配置已被其它更新修改，版本不一致，拒绝覆盖：key=" + key
                + "，期望版本 " + expectedVersion + "，当前版本 " + actualVersion);
        this.key = key;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public String getKey() {
        return key;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public Long getActualVersion() {
        return actualVersion;
    }
}