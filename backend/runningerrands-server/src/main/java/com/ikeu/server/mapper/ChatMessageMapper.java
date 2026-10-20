package com.ikeu.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikeu.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 聊天消息 Mapper，提供消息实体的 CRUD 及联系人查询、未读统计、软删除等操作。
 * @author ikeu
 * @since 2025/05/21
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 查询与指定用户有过聊天记录的所有联系人ID。
     *
     * @param userId 当前用户ID
     * @return 联系人用户ID列表
     */
    @Select("SELECT DISTINCT CASE WHEN sender_id = #{userId} THEN receiver_id ELSE sender_id END " +
            "FROM chat_message WHERE (sender_id = #{userId} OR receiver_id = #{userId}) AND is_deleted = 0")
    List<Long> selectContactUserIds(@Param("userId") Long userId);

    /**
     * 批量获取每个会话的最后一条消息，单次查询代替 N 次逐会话查询。
     *
     * @param userId 当前用户ID
     * @return 各会话最后一条消息的列表
     */
    @Select("SELECT m.* FROM chat_message m WHERE m.id IN (" +
            "  SELECT MAX(id) FROM chat_message " +
            "  WHERE (sender_id = #{userId} OR receiver_id = #{userId}) AND is_deleted = 0 " +
            "  GROUP BY CASE WHEN sender_id = #{userId} THEN receiver_id ELSE sender_id END" +
            ")")
    List<ChatMessage> selectLastMessages(@Param("userId") Long userId);

    /**
     * 批量统计各发送者的未读消息数量。
     *
     * @param userId 接收者用户ID
     * @return 发送者ID到未读计数的映射
     */
    @Select("SELECT sender_id, COUNT(*) AS cnt FROM chat_message " +
            "WHERE receiver_id = #{userId} AND is_read = 0 AND is_deleted = 0 " +
            "GROUP BY sender_id")
    List<Map<String, Object>> countUnreadsBatch(@Param("userId") Long userId);

    /**
     * 将指定发送者发送给当前用户的所有消息标记为已读。
     *
     * @param userId 当前用户ID
     * @param senderId 发送者ID
     */
    @Update("UPDATE chat_message SET is_read = 1 " +
            "WHERE sender_id = #{senderId} AND receiver_id = #{userId} AND is_read = 0")
    void markRead(@Param("userId") Long userId, @Param("senderId") Long senderId);

    /**
     * 软删除指定消息。
     *
     * @param id 消息ID
     */
    @Update("UPDATE chat_message SET is_deleted = 1 WHERE id = #{id}")
    void softDeleteById(@Param("id") Long id);

    /**
     * 撤回指定消息（标记为已撤回，不删除记录）。
     *
     * @param id 消息ID
     */
    @Update("UPDATE chat_message SET is_recalled = 1 WHERE id = #{id}")
    void recallById(@Param("id") Long id);

    /**
     * 分页查询与指定用户的双向聊天记录。
     *
     * <p>仅查询未删除（is_deleted=0）的消息，按创建时间倒序排列。
     * 查询条件覆盖双向通信：(sender=user AND receiver=target) OR (sender=target AND receiver=user)。
     *
     * @param page         分页对象
     * @param userId       当前用户ID
     * @param targetUserId 对话目标用户ID
     * @return 分页聊天消息列表
     */
    Page<ChatMessage> selectHistory(Page<ChatMessage> page,
                                    @Param("userId") Long userId,
                                    @Param("targetUserId") Long targetUserId);
}
