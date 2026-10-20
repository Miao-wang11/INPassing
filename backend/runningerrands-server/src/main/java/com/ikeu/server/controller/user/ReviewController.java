package com.ikeu.server.controller.user;

import com.ikeu.common.constant.MessageConstant;
import com.ikeu.common.context.BaseContext;
import com.ikeu.common.result.Result;
import com.ikeu.model.dto.ReviewCreateDTO;
import com.ikeu.model.dto.ReviewUpdateDTO;
import com.ikeu.model.vo.ReviewVO;
import com.ikeu.server.annotation.RequireCertify;
import com.ikeu.server.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 评价模块接口，提供评价创建、修改、删除、查看及追加评价等功能。
 * @author ikeu
 * @since 2025/05/26
 */
@Tag(name = "用户端-评价模块接口", description = "任务完成后的互评")
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 创建评价。
     *
     * <p>委托 {@link ReviewService#createReview} 校验订单已完成、评价者与目标用户的关联关系、
     * 尚未评价过此订单等条件后，保存评价记录到 review 表。
     *
     * @param reviewDTO 评价创建DTO（订单ID、目标用户ID、评分、内容）
     * @return 操作结果
     */
    @RequireCertify
    @Operation(summary = "创建评价")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody ReviewCreateDTO reviewDTO) {
        Long reviewerId = BaseContext.getCurrentId();
        reviewService.createReview(reviewerId, reviewDTO);
        return Result.success(MessageConstant.REVIEW_CREATE_SUCCESS);
    }

    /**
     * 修改已创建的评论。
     *
     * <p>委托 {@link ReviewService#updateReview} 校验评价归属（只能修改自己的评价）、
     * 未超过修改时限后，更新评分和内容字段。
     *
     * @param reviewId 评价ID
     * @param reviewUpdateDTO 评价修改DTO（新评分、新内容）
     * @return 操作结果
     */
    @RequireCertify
    @Operation(summary = "修改评价")
    @PutMapping("/{reviewId}")
    public Result<Void> update(@PathVariable Long reviewId,
                               @Valid @RequestBody ReviewUpdateDTO reviewUpdateDTO) {
        Long reviewerId = BaseContext.getCurrentId();
        reviewService.updateReview(reviewerId, reviewId, reviewUpdateDTO);
        return Result.success(MessageConstant.REVIEW_MODIFIED);
    }

    /**
     * 删除指定的评价。
     *
     * <p>委托 {@link ReviewService#deleteReview} 校验评价归属（只能删除自己的评价）后
     * 执行物理删除。
     *
     * @param reviewId 评价ID
     * @return 操作结果
     */
    @RequireCertify
    @Operation(summary = "删除评价")
    @DeleteMapping("/{reviewId}")
    public Result<Void> delete(@PathVariable Long reviewId) {
        Long reviewerId = BaseContext.getCurrentId();
        reviewService.deleteReview(reviewerId, reviewId);
        return Result.success(MessageConstant.REVIEW_DELETED);
    }

    /**
     * 查看指定用户收到的所有评价。
     *
     * <p>委托 {@link ReviewService#listUserReviews} 查询 targetUserId 收到的所有评价，
     * 包含评价者信息和追评内容。
     *
     * @param targetUserId 目标用户ID
     * @return 评价列表
     */
    @Operation(summary = "查看指定用户收到的评价")
    @GetMapping("/user/{targetUserId}")
    public Result<List<ReviewVO>> listUserReviews(@PathVariable Long targetUserId) {
        return Result.success(reviewService.listUserReviews(targetUserId));
    }

    /**
     * 查看指定任务的所有评价。
     *
     * <p>委托 {@link ReviewService#getTaskReviews} 查询该任务关联订单的所有评价记录。
     *
     * @param taskId 任务ID
     * @return 评价列表
     */
    @Operation(summary = "查看指定任务的所有评价")
    @GetMapping("/task/{taskId}")
    public Result<List<ReviewVO>> getTaskReviews(@PathVariable Long taskId) {
        return Result.success(reviewService.getTaskReviews(taskId));
    }

    /**
     * 对已有评价进行追加评论。
     *
     * <p>委托 {@link ReviewService#createFollowUp} 校验原评价存在、当前用户为评价目标用户
     * 且尚未追加过评价后，保存追评内容。
     *
     * @param reviewId 原评价ID
     * @param content 追加评论内容
     * @return 操作结果
     */
    @RequireCertify
    @Operation(summary = "追加评价")
    @PostMapping("/{reviewId}/followup")
    public Result<Void> createFollowUp(@PathVariable Long reviewId,
                                       @RequestParam String content) {
        Long userId = BaseContext.getCurrentId();
        reviewService.createFollowUp(userId, reviewId, content);
        return Result.success(MessageConstant.FOLLOWUP_CREATE_SUCCESS);
    }
}
