package com.ikeu.common.constant;

import java.util.Map;
import java.util.Set;

/**
 * 任务类型常量
 * type 为单值大类，subType 为 JSON 数组
 */
public final class TaskTypeConstant {

    private TaskTypeConstant() {}

    // ========== 任务大类 ==========
    public static final String TYPE_EXPRESS_PICKUP = "代取快递";
    public static final String TYPE_MEAL_PICKUP = "代拿餐食";
    public static final String TYPE_CAMPUS_ERRAND = "校内代办";
    public static final String TYPE_CAMPUS_EXPRESS = "代购物品";
    public static final String TYPE_GENERAL_ERRAND = "通用代办";

    public static final Set<String> ALL_TYPES = Set.of(
            TYPE_EXPRESS_PICKUP, TYPE_MEAL_PICKUP, TYPE_CAMPUS_ERRAND, TYPE_CAMPUS_EXPRESS, TYPE_GENERAL_ERRAND);

    // ========== 任务小类 — 代取快递 ==========
    public static final String SUB_SMALL_PACKAGE = "小件快递";
    public static final String SUB_MEDIUM_PACKAGE = "中件快递";
    public static final String SUB_LARGE_PACKAGE = "大件快递";

    // ========== 任务小类 — 代拿餐食 ==========
    public static final String SUB_CAMPUS_FOOD = "校内餐饮";
    public static final String SUB_OFF_CAMPUS_FOOD = "校外餐饮";
    public static final String SUB_MILK_TEA_COFFEE = "奶茶咖啡代取";

    // ========== 任务小类 — 校内代办 ==========
    public static final String SUB_PRINT = "资料打印";
    public static final String SUB_LIBRARY_RETURN = "图书馆还书";
    public static final String SUB_SHOPPING = "物品急送";
    public static final String SUB_TRASH = "帮扔杂物";
    public static final String SUB_QUEUE_WAIT = "办事代排";

    // ========== 任务小类 — 代购物品 ==========
    public static final String SUB_CAMPUS_SHOPPING = "校内代购";
    public static final String SUB_OFF_CAMPUS_SHOPPING = "校外代购";
    public static final String SUB_PAPER_DELIVERY = "纸品速达";
    public static final String SUB_STATIONERY_DELIVERY = "文具急送";

    public static final Set<String> ALL_SUB_TYPES = Set.of(
            SUB_SMALL_PACKAGE, SUB_MEDIUM_PACKAGE, SUB_LARGE_PACKAGE,
            SUB_CAMPUS_FOOD, SUB_OFF_CAMPUS_FOOD, SUB_MILK_TEA_COFFEE,
            SUB_PRINT, SUB_LIBRARY_RETURN, SUB_SHOPPING, SUB_TRASH, SUB_QUEUE_WAIT,
            SUB_PAPER_DELIVERY, SUB_STATIONERY_DELIVERY, SUB_CAMPUS_SHOPPING, SUB_OFF_CAMPUS_SHOPPING);

    /** 无需用户手动选择送达地址的小类 → 默认送达地址 */
    public static final Map<String, String> DEFAULT_DELIVERY_MAP = Map.of(
            SUB_LIBRARY_RETURN, "图书馆",
            SUB_TRASH, "杂物处理点",
            SUB_QUEUE_WAIT, "无需送达");

}