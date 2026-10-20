package com.ikeu.server.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikeu.common.constant.MessageConstant;
import com.ikeu.common.constant.StatusConstant;
import com.ikeu.common.exception.BusinessException;
import com.ikeu.common.exception.NotFoundException;
import com.ikeu.model.entity.ChatMessage;
import com.ikeu.model.entity.User;
import com.ikeu.model.vo.ChatVO;
import com.ikeu.model.vo.ContactVO;
import com.ikeu.server.mapper.ChatMessageMapper;
import com.ikeu.server.mapper.UserMapper;
import com.ikeu.server.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 聊天服务实现，处理消息发送、历史查询、联系人列表、已读标记、删除和撤回等功能。
 * @author ikeu
 * @since 2026/05/14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 发送消息并实时推送给收发双方。
     *
     * <p>校验 senderId 非空后：
     * <ol>
     *   <li>构建 ChatMessage 实体（isRead=未读，isDeleted=未删除，isRecalled=未撤回）并持久化</li>
     *   <li>查询发送者信息构造 ChatVO（含 messageId、发送者昵称头像、内容、类型）</li>
     *   <li>通过 STOMP 推送到接收者的 /queue/chat 目标</li>
     *   <li>同步推送给发送者，使其获取服务端生成的 messageId，无需刷新即可操作删除/撤回</li>
     * </ol>
     *
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     * @param content 消息内容
     * @param messageType 消息类型
     */
    @Override
    @Transactional
    public void sendMessage(Long senderId, Long receiverId, String content, Integer messageType) {
        if (senderId == null) {
            throw new BusinessException(MessageConstant.SENDER_ID_NULL);
        }
        ChatMessage msg = ChatMessage.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .content(content)
                .messageType(messageType != null ? messageType : 1)
                .isRead(StatusConstant.UNREAD)
                .isDeleted(StatusConstant.MESSAGE_NOT_DELETED)
                .isRecalled(StatusConstant.MESSAGE_NOT_RECALLED)
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageMapper.insert(msg);

        User sender = userMapper.selectById(senderId);
        ChatVO vo = ChatVO.builder()
                .messageId(msg.getId())
                .senderId(senderId)
                .senderNickname(sender != null ? sender.getNickname() : "")
                .senderAvatar(sender != null ? sender.getAvatarUrl() : "")
                .receiverId(receiverId)
                .content(content)
                .messageType(msg.getMessageType())
                .isRead(StatusConstant.MESSAGE_NOT_DELETED)
                .messageAction(StatusConstant.MESSAGE_NOT_RECALLED)
                .createdAt(msg.getCreatedAt())
                .build();

        messagingTemplate.convertAndSendToUser(
                String.valueOf(receiverId), "/queue/chat", vo);
        messagingTemplate.convertAndSendToUser(
                String.valueOf(senderId), "/queue/chat", vo);
    }

    /**
     * 获取与指定用户的双向聊天记录，分页按创建时间倒序排列。
     *
     * <p>查询删选条件：is_deleted=0 且 (sender=user AND receiver=target) OR (sender=target AND receiver=user)。
     * 批量获取涉及用户的昵称头像填充，已撤回消息内容替换为撤回提示文本。
     *
     * @param userId 当前用户ID
     * @param targetUserId 目标用户ID
     * @param page 页码
     * @param size 每页条数
     * @return 聊天记录列表
     */
    @Override
    public List<ChatVO> getHistory(Long userId, Long targetUserId, int page, int size) {
        Page<ChatMessage> p = chatMessageMapper.selectHistory(
                new Page<>(page, size), userId, targetUserId);

        if (p.getRecords().isEmpty()) return Collections.emptyList();

        Set<Long> userIds = new HashSet<>();
        p.getRecords().forEach(m -> {
            userIds.add(m.getSenderId());
            userIds.add(m.getReceiverId());
        });
        Map<Long, User> userMap = userMapper.selectBatchIds(new ArrayList<>(userIds))
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        List<ChatVO> list = new ArrayList<>();
        for (ChatMessage m : p.getRecords()) {
            User sender = userMap.get(m.getSenderId());
            boolean isRecalled = Integer.valueOf(1).equals(m.getIsRecalled());
            list.add(ChatVO.builder()
                    .messageId(m.getId())
                    .senderId(m.getSenderId())
                    .senderNickname(sender != null ? sender.getNickname() : "")
                    .senderAvatar(sender != null ? sender.getAvatarUrl() : "")
                    .receiverId(m.getReceiverId())
                    .content(isRecalled ? MessageConstant.MESSAGE_RECALLED_CONTENT : m.getContent())
                    .messageType(m.getMessageType())
                    .isRead(m.getIsRead())
                    .messageAction(isRecalled ? 2 : 0)
                    .createdAt(m.getCreatedAt())
                    .build());
        }
        return list;
    }

    /**
     * 获取联系人列表，按最近消息时间倒序排列。
     *
     * <p>实现逻辑：查询所有有过聊天的用户ID → 批量获取用户信息 →
     * 批量获取每个会话的最后一条消息（单次 SQL）→ 批量统计各发送者未读计数（单次 SQL）→
     * 构造 ContactVO 列表（含最后消息摘要、未读计数），按最后消息时间倒序排列。
     *
     * @param userId 当前用户ID
     * @return 联系人列表
     */
    @Override
    public List<ContactVO> getContacts(Long userId) {
        List<Long> contactIds = chatMessageMapper.selectContactUserIds(userId);
        if (contactIds.isEmpty()) return Collections.emptyList();

        Map<Long, User> userMap = userMapper.selectBatchIds(contactIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, ChatMessage> lastMsgMap = new HashMap<>();
        List<ChatMessage> lastMessages = chatMessageMapper.selectLastMessages(userId);
        for (ChatMessage msg : lastMessages) {
            Long contactId = msg.getSenderId().equals(userId) ? msg.getReceiverId() : msg.getSenderId();
            lastMsgMap.put(contactId, msg);
        }

        Map<Long, Integer> unreadMap = new HashMap<>();
        List<Map<String, Object>> unreadCounts = chatMessageMapper.countUnreadsBatch(userId);
        for (Map<String, Object> row : unreadCounts) {
            Long senderId = ((Number) row.get("sender_id")).longValue();
            Integer cnt = ((Number) row.get("cnt")).intValue();
            unreadMap.put(senderId, cnt);
        }

        List<ContactVO> contacts = new ArrayList<>();
        for (Long contactId : contactIds) {
            User contact = userMap.get(contactId);
            if (contact == null) continue;

            ChatMessage lastMsg = lastMsgMap.get(contactId);
            int unread = unreadMap.getOrDefault(contactId, StatusConstant.UNREAD);

            contacts.add(ContactVO.builder()
                    .userId(contactId)
                    .nickname(contact.getNickname())
                    .avatarUrl(contact.getAvatarUrl())
                    .lastMessage(lastMsg != null
                            ? (Integer.valueOf(1).equals(lastMsg.getIsRecalled())
                                    ? MessageConstant.MESSAGE_RECALLED_CONTENT
                                    : lastMsg.getContent())
                            : "")
                    .lastMessageTime(lastMsg != null ? lastMsg.getCreatedAt() : null)
                    .unreadCount(unread)
                    .build());
        }

        contacts.sort(Comparator.comparing(ContactVO::getLastMessageTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return contacts;
    }

    /**
     * 将指定发送者发来的所有未读消息标记为已读。
     *
     * @param userId 当前用户ID（接收者）
     * @param senderId 发送者ID
     */
    @Override
    @Transactional
    public void markRead(Long userId, Long senderId) {
        chatMessageMapper.markRead(userId, senderId);
    }

    /**
     * 软删除自己发送的消息。
     *
     * <p>校验逻辑：消息存在 → 当前用户为发送者 → 未被删除（已删除幂等返回）→
     * 执行软删除，通过 STOMP 通知接收者消息已被删除。
     *
     * @param userId 当前用户ID
     * @param messageId 消息ID
     */
    @Override
    @Transactional
    public void deleteMessage(Long userId, Long messageId) {
        ChatMessage msg = chatMessageMapper.selectById(messageId);
        if (msg == null) {
            throw new NotFoundException(MessageConstant.MESSAGE_NOT_FOUND);
        }
        if (!msg.getSenderId().equals(userId)) {
            throw new BusinessException(MessageConstant.MESSAGE_NOT_YOURS);
        }
        if (Integer.valueOf(1).equals(msg.getIsDeleted())) {
            return;
        }

        chatMessageMapper.softDeleteById(messageId);

        ChatVO vo = ChatVO.builder()
                .messageId(messageId)
                .messageAction(1)
                .build();
        messagingTemplate.convertAndSendToUser(
                String.valueOf(msg.getReceiverId()), "/queue/chat", vo);
    }

    /**
     * 撤回自己发送的消息（5分钟内可撤回）。
     *
     * <p>校验逻辑：消息存在 → 当前用户为发送者 → 未被删除 → 未被撤回（已撤回幂等返回）→
     * 发送时间不超过5分钟 → 执行撤回（标记 is_recalled=1），
     * 通过 STOMP 推送撤回后的消息 VO（内容替换为撤回提示，messageAction=2）给接收者。
     *
     * @param userId 当前用户ID
     * @param messageId 消息ID
     */
    @Override
    @Transactional
    public void recallMessage(Long userId, Long messageId) {
        ChatMessage msg = chatMessageMapper.selectById(messageId);
        if (msg == null) {
            throw new NotFoundException(MessageConstant.MESSAGE_NOT_FOUND);
        }
        if (!msg.getSenderId().equals(userId)) {
            throw new BusinessException(MessageConstant.MESSAGE_NOT_YOURS);
        }
        if (Integer.valueOf(1).equals(msg.getIsDeleted())) {
            throw new BusinessException(MessageConstant.MESSAGE_ALREADY_DELETED);
        }
        if (Integer.valueOf(1).equals(msg.getIsRecalled())) {
            return;
        }

        if (msg.getCreatedAt().plusMinutes(5).isBefore(LocalDateTime.now())) {
            throw new BusinessException(MessageConstant.MESSAGE_RECALL_TIMEOUT);
        }

        chatMessageMapper.recallById(messageId);

        User sender = userMapper.selectById(userId);
        ChatVO vo = ChatVO.builder()
                .messageId(messageId)
                .senderId(userId)
                .senderNickname(sender != null ? sender.getNickname() : "")
                .senderAvatar(sender != null ? sender.getAvatarUrl() : "")
                .receiverId(msg.getReceiverId())
                .content(MessageConstant.MESSAGE_RECALLED_CONTENT)
                .messageType(msg.getMessageType())
                .isRead(msg.getIsRead())
                .messageAction(2)
                .createdAt(msg.getCreatedAt())
                .build();
        messagingTemplate.convertAndSendToUser(
                String.valueOf(msg.getReceiverId()), "/queue/chat", vo);
    }
}
