package com.ikeu.server.service;

import com.ikeu.model.vo.CategoryPieVO;
import com.ikeu.model.vo.DashboardVO;
import com.ikeu.model.vo.TrendPointVO;

import java.util.List;

/**
 * 管理端仪表盘服务接口，按统计维度拆分为独立方法。
 * @author ikeu
 * @since 2026/06/11
 */
public interface AdminDashboardService {

    /**
     * 统计卡片摘要数据，含用户/任务/订单总数、认证跑腿员数、今日新增/完成量及今日交易额。
     *
     * @return 仪表盘标量指标 VO
     */
    DashboardVO getDashboardSummary();

    /**
     * 近 7 天新增用户趋势，逐日统计注册用户数，无数据日期补零填充。
     *
     * @return 日期-数量趋势点列表
     */
    List<TrendPointVO> getUserTrend();

    /**
     * 近 7 天收入趋势，聚合已完成任务报酬金额，无数据日期补零填充。
     *
     * @return 日期-金额趋势点列表
     */
    List<TrendPointVO> getRevenueTrend();

    /**
     * 任务分类占比，按任务类型分组统计数量。
     *
     * @return 分类名称-数量列表
     */
    List<CategoryPieVO> getTaskCategories();

    /**
     * 订单状态分布，按 5 种订单状态统计非软删除订单数。
     *
     * @return 状态名称-数量列表
     */
    List<CategoryPieVO> getOrderStatusDistribution();
}
