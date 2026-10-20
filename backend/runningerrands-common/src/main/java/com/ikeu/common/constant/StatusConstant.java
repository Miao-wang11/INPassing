package com.ikeu.common.constant;

/**
 * 通用状态常量
 */
public final class StatusConstant {

    private StatusConstant() {}

    // ========== 通用启用/禁用 ==========
    public static final Integer ENABLE = 1;
    public static final Integer DISABLE = 0;

    // ========== 是否类状态 ==========
    public static final Integer YES = 1;
    public static final Integer NO = 0;

    // ========== 用户认证状态 ==========
    public static final Integer CERTIFY_UNCERTIFIED = 0;   // 未认证
    public static final Integer CERTIFY_AUDITING = 1;      // 审核中
    public static final Integer CERTIFY_APPROVED = 2;      // 已认证
    public static final Integer CERTIFY_REJECTED = 3;      // 认证驳回

    // ========== 跑腿员在线状态 ==========
    public static final Integer RUNNER_OFFLINE = 0;
    public static final Integer RUNNER_ONLINE = 1;

    // ========== 消息状态 ==========
    public static final Integer UNREAD = 0;
    public static final Integer READ = 1;
    public static final Integer MESSAGE_NOT_DELETED = 0;   // 消息已删除
    public static final Integer MESSAGE_NOT_RECALLED = 0;  // 消息已撤回

    // ========== 任务状态 ==========
    public static final Integer TASK_WAITING = 1;          // 待接单
    public static final Integer TASK_ACCEPTED = 2;         // 已接单
    public static final Integer TASK_DELIVERING = 3;       // 配送中
    public static final Integer TASK_WAIT_CONFIRM = 4;     // 待确认
    public static final Integer TASK_COMPLETED = 5;        // 已完成
    public static final Integer TASK_CANCELLED = 6;        // 已取消

    // ========== 订单状态 ==========
    public static final Integer ORDER_WAIT_PICKUP = 1;     // 待取货
    public static final Integer ORDER_DELIVERING = 2;      // 配送中
    public static final Integer ORDER_WAIT_CONFIRM = 3;    // 待确认
    public static final Integer ORDER_COMPLETED = 4;       // 已完成
    public static final Integer ORDER_CANCELLED = 5;       // 已取消
    public static final Integer ORDER_NOT_DELETED = 0;     // 未删除
    public static final Integer ORDER_DELETED = 1;         // 已删除

    // ========== 交易类型 ==========
    public static final Integer TRANSACTION_EXPENSE = 1;   // 支出
    public static final Integer TRANSACTION_INCOME = 2;    // 收入
    public static final Integer TRANSACTION_RECHARGE = 3;  // 充值
    public static final Integer TRANSACTION_WITHDRAW = 4;  // 提现
    public static final Integer TRANSACTION_REFUND = 5;    // 退款

    // ========== 通知类型 ==========
    public static final int NOTICE_SYSTEM = 1;             // 系统通知
    public static final int NOTICE_ORDER = 2;              // 订单流转通知
    public static final int NOTICE_ACTIVITY = 3;           // 活动通知

}