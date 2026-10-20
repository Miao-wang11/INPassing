package com.ikeu.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikeu.model.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评价 Mapper，提供评价实体的 CRUD 及评分计算等自定义查询。
 * @author ikeu
 * @since 2025/05/21
 */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /**
     * 在数据库侧直接计算目标用户的平均评分，避免全量拉取到内存。
     *
     * @param targetUserId 目标用户ID
     * @return 平均评分，无评价时返回0
     */
    @Select("SELECT COALESCE(AVG(rating), 0) FROM review " +
            "WHERE target_user_id = #{targetUserId} AND parent_id IS NULL AND rating IS NOT NULL")
    Double selectAvgRatingByTargetUser(@Param("targetUserId") Long targetUserId);

    /**
     * 批量查询用户已评价的任务ID集合，用于任务列表展示"已评价"标记。
     *
     * @param taskIds 任务ID集合
     * @param reviewerId 评价者ID
     * @return 已评价的任务ID列表
     */
    List<Long> selectReviewedTaskIds(@Param("taskIds") List<Long> taskIds,
                                     @Param("reviewerId") Long reviewerId);
}
