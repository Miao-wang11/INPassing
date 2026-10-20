package com.ikeu.common.constant;

/**
 * JWT Claims 常量 —— 定义双令牌机制中使用的所有 JWT 声明键和类型值。
 *
 * <p>令牌类型区分：access token 用于鉴权（短有效期），refresh token 用于续签（长有效期，
 * 携带 jti 唯一标识以支持 Redis 跟踪和令牌轮换）。
 */
public final class JwtClaimsConstant {

    private JwtClaimsConstant() {}

    /** 管理端用户 ID */
    public static final String ADMIN_ID = "adminId";

    /** 用户端用户 ID */
    public static final String USER_ID = "userId";

    /** 令牌类型键：值为 {@link #TOKEN_TYPE_ACCESS} 或 {@link #TOKEN_TYPE_REFRESH} */
    public static final String TOKEN_TYPE = "type";

    /** JWT ID —— refresh token 的唯一标识，用于 Redis 存储和轮换验证 */
    public static final String JTI = "jti";

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

}
