package com.pinliao.backend.user;

/**
 * 用户 / 求职者模块专属业务码（契约 §5）。
 *
 * <p>与既有模块保持一致：40910 同时用于「手机号已注册」与「已收藏」，
 * 40410 用于「资源不存在或无权查看」，42201 用于字段 / 业务参数校验。</p>
 */
public final class UserCodes {

    /** 40410 画像 / 简历不存在或无权查看（不泄露存在性） */
    public static final int NOT_FOUND = 40410;

    /** 40910 手机号已被占用（与注册重复手机号同码） */
    public static final int PHONE_TAKEN = 40910;

    /** 40910 已收藏过该职位（契约 §5.2 / §11.1） */
    public static final int FAVORITE_EXISTS = 40910;

    /** 42201 字段 / 业务参数校验失败 */
    public static final int INVALID = 42201;

    private UserCodes() {
    }
}
