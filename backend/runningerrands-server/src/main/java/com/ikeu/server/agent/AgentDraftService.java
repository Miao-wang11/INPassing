package com.ikeu.server.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikeu.common.constant.TaskTypeConstant;
import com.ikeu.common.exception.BusinessException;
import com.ikeu.common.exception.ForbiddenException;
import com.ikeu.common.money.TaskFeeBreakdown;
import com.ikeu.model.dto.TaskPublishDTO;
import com.ikeu.model.entity.Task;
import com.ikeu.model.entity.User;
import com.ikeu.model.entity.UserAddress;
import com.ikeu.server.mapper.UserAddressMapper;
import com.ikeu.server.mapper.UserMapper;
import com.ikeu.server.service.CampusCatalogService;
import com.ikeu.server.service.ServiceOfferService;
import com.ikeu.server.service.TaskService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Java owns all preview, money, confirmation and business write operations.
 * Python sees NONE of the secret confirmation token or pay password.
 * MySQL row-level lock + same JDBC transaction as existing Java services gives
 * once-per-operation execution across concurrent requests and graph retries.
 */
@Service
@RequiredArgsConstructor
public class AgentDraftService {
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal MAX_SERVICE = new BigDecimal("500.00");
    private static final BigDecimal MAX_GOODS = new BigDecimal("5000.00");
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final CampusCatalogService campuses;
    private final TaskService taskService;
    private final ServiceOfferService offers;
    private final UserMapper users;
    private final UserAddressMapper addresses;
    private final ObjectMapper mapper;
    private final Validator validator;

    public record TaskInput(String type, String subType, String publicDesc, String pickupAddress,
                            Long deliveryAddressId, String taskSpecs, BigDecimal tip,
                            BigDecimal deliveryFee, BigDecimal productCost, Integer expireMinutes) {}
    public record OfferInput(Long offerId, String pickupAddress, Long deliveryAddressId,
                             BigDecimal shoppingBudget, String privateNote) {}
    public record DraftInput(String mode, TaskInput task, OfferInput offer) {}
    public record Prepared(String operationId, String confirmToken, String mode, String description,
                           BigDecimal serviceFee, BigDecimal shoppingBudget, BigDecimal total,
                           LocalDateTime expiresAt, String warning) {}
    public record Receipt(String operationId, String status, Long taskId, Long orderId,
                          BigDecimal total) {}
    private record Snapshot(String operationId, long userId, long campusId, String mode, String payload,
                            String payloadHash, String confirmHash, BigDecimal quotedFee, BigDecimal quotedGoods,
                            BigDecimal total, LocalDateTime expiresAt, String status,
                            Long taskId, Long orderId) {}

    private static LocalDateTime now() { return LocalDateTime.now(CAMPUS_ZONE); }
    private static BigDecimal zero(BigDecimal n) { return n == null ? BigDecimal.ZERO : n; }
    private static BigDecimal bounded(BigDecimal value, BigDecimal limit, String field) {
        if (value == null || value.signum() < 0 || value.compareTo(limit) > 0 ||
                value.stripTrailingZeros().scale() > 2)
            throw new BusinessException(field + "格式或范围无效");
        return value;
    }
    private static String text(String s, int limit, String name) {
        if (s == null || s.isBlank() || s.length() > limit) throw new BusinessException(name + "不合法");
        return s.trim();
    }
    private static String sha(String data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
    private void checkOwnerAddress(Long uid, Long addressId) {
        if (addressId == null || addressId <= 0) throw new BusinessException("请选择本人地址簿中的送达地址");
        UserAddress address = addresses.selectById(addressId);
        if (address == null || !uid.equals(address.getUserId()))
            throw new ForbiddenException("送达地址不属于当前用户");
    }
    private void checkFunds(Long uid, BigDecimal amount) {
        User user = users.selectById(uid);
        if (user == null || user.getBalance() == null || user.getBalance().compareTo(amount) < 0)
            throw new BusinessException("账户余额不足或账户不可用");
    }
    private TaskPublishDTO publicDto(TaskInput input, String payPassword) {
        if (input == null) throw new BusinessException("公开委托字段不能为空");
        TaskPublishDTO dto = new TaskPublishDTO();
        dto.setType(text(input.type(), 32, "任务类型"));
        if (!TaskTypeConstant.ALL_TYPES.contains(dto.getType()))
            throw new BusinessException("任务类型不支持");
        if (input.subType() != null && !input.subType().isBlank()) {
            if (!TaskTypeConstant.ALL_SUB_TYPES.contains(input.subType()))
                throw new BusinessException("任务子类型不支持");
            dto.setSubType(input.subType());
        }
        dto.setPublicDesc(text(input.publicDesc(), 256, "公开任务描述"));
        dto.setPickupAddress(text(input.pickupAddress(), 255, "取件地点"));
        dto.setDeliveryAddressId(input.deliveryAddressId());
        dto.setTip(bounded(input.tip(), new BigDecimal("50.00"), "小费"));
        dto.setDeliveryFee(bounded(zero(input.deliveryFee()), MAX_SERVICE, "配送费"));
        dto.setProductCost(bounded(zero(input.productCost()), MAX_GOODS, "商品预算"));
        int ttl = input.expireMinutes() == null ? 60 : input.expireMinutes();
        if (ttl < 10 || ttl > 1440) throw new BusinessException("任务接单有效期必须为10～1440分钟");
        dto.setExpireMinutes(ttl);
        if (input.taskSpecs() != null && input.taskSpecs().length() > 2048)
            throw new BusinessException("任务规格过长");
        if (input.taskSpecs() != null && !input.taskSpecs().isBlank()) {
            try {
                if (!mapper.readTree(input.taskSpecs()).isObject())
                    throw new BusinessException("任务规格必须是 JSON 对象");
            } catch (JsonProcessingException ex) {
                throw new BusinessException("任务规格不是合法 JSON");
            }
        }
        dto.setTaskSpecs(input.taskSpecs());
        dto.setPayPassword(payPassword);
        Set<ConstraintViolation<TaskPublishDTO>> failures = validator.validate(dto);
        // During PREPARE the password is an intentionally non-secret marker, not submitted to a transaction.
        if (!failures.isEmpty()) throw new BusinessException("任务参数不合法");
        return dto;
    }
    private TaskFeeBreakdown fee(TaskPublishDTO dto) {
        try {
            BigDecimal tip = dto.getTip(), delivery = zero(dto.getDeliveryFee()), goods = zero(dto.getProductCost());
            return TaskFeeBreakdown.of(tip, delivery, goods, tip.add(delivery).add(goods));
        } catch (IllegalArgumentException ex) { throw new BusinessException("任务费用分项不合法"); }
    }

    /** PREPARE is the only public draft creation point. No task, order or debit occurs here. */
    @Transactional
    public Prepared prepare(Long uid, DraftInput input) {
        Long campusId = campuses.requireUserCampus(uid);
        if (input == null) throw new BusinessException("草稿不能为空");
        TaskFeeBreakdown fees;
        String desc;
        if ("PUBLIC_TASK".equals(input.mode())) {
            if (input.offer() != null) throw new BusinessException("草稿模式冲突");
            TaskPublishDTO dto = publicDto(input.task(), "preview-only");
            checkOwnerAddress(uid, dto.getDeliveryAddressId());
            fees = fee(dto);
            desc = dto.getPublicDesc();
        } else if ("SERVICE_OFFER".equals(input.mode())) {
            if (input.task() != null || input.offer() == null) throw new BusinessException("草稿模式冲突");
            OfferInput offerInput = input.offer();
            checkOwnerAddress(uid, offerInput.deliveryAddressId());
            text(offerInput.pickupAddress(), 255, "取件地点");
            if (offerInput.privateNote() != null && offerInput.privateNote().length() > 256)
                throw new BusinessException("私密备注过长");
            if (offerInput.offerId() == null || offerInput.offerId() <= 0)
                throw new BusinessException("服务不存在");
            ServiceOfferService.OfferView offer = offers.detail(uid, offerInput.offerId());
            if (uid.equals(offer.providerId())) throw new ForbiddenException("不能预约自己的服务");
            if (!campusId.equals(offer.campusId()) || offer.status() != 1 ||
                    offer.availableFrom() == null || offer.availableUntil() == null ||
                    now().isBefore(offer.availableFrom()) || !now().isBefore(offer.availableUntil()))
                throw new BusinessException("服务当前无法预约");
            BigDecimal service = bounded(offer.servicePrice(), MAX_SERVICE, "服务报价");
            BigDecimal goods = bounded(zero(offerInput.shoppingBudget()), MAX_GOODS, "商品预算");
            try { fees = TaskFeeBreakdown.of(service, BigDecimal.ZERO, goods, service.add(goods)); }
            catch (IllegalArgumentException ex) { throw new BusinessException("服务费用不合法"); }
            desc = offer.title();
        } else {
            throw new BusinessException("草稿模式不支持");
        }
        checkFunds(uid, fees.total());
        String operationId = UUID.randomUUID().toString();
        String token = randomToken();
        LocalDateTime expiry = now().plusMinutes(10);
        String payload;
        try { payload = mapper.writeValueAsString(input); }
        catch (JsonProcessingException ex) { throw new BusinessException("草稿参数无法保存"); }
        int n = jdbc.update("INSERT INTO agent_operation " +
                        "(operation_id,user_id,campus_id,mode,payload_json,payload_hash,confirm_token_hash," +
                        "quoted_service_fee,quoted_goods_budget,quoted_total,expires_at,status) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,'PREPARED')",
                operationId, uid, campusId, input.mode(), payload, sha(payload), sha(token),
                fees.serviceFee(), fees.shoppingBudget(), fees.total(), expiry);
        if (n != 1) throw new BusinessException("草稿生成失败");
        return new Prepared(operationId, token, input.mode(), desc, fees.serviceFee(),
                fees.shoppingBudget(), fees.total(), expiry,
                "报价以当前校区规则为准；截止时间不是接单有效期，具体交付时间须双方确认");
    }

    private Snapshot row(Long idOwner, String opId, boolean forUpdate) {
        String sql = "SELECT operation_id,user_id,campus_id,mode,payload_json,payload_hash,confirm_token_hash," +
                "quoted_service_fee,quoted_goods_budget,quoted_total,expires_at,status,task_id,order_id " +
                "FROM agent_operation WHERE operation_id=? AND user_id=?" + (forUpdate ? " FOR UPDATE" : "");
        List<Snapshot> found = jdbc.query(sql, (rs, i) -> new Snapshot(rs.getString(1),
                rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBigDecimal(10),
                // Read MySQL DATETIME without timezone shifting via java.sql.Timestamp.
                rs.getObject(11, LocalDateTime.class), rs.getString(12),
                (Long) rs.getObject(13), (Long) rs.getObject(14)), opId, idOwner);
        if (found.isEmpty()) throw new ForbiddenException("草稿不存在或不属于当前账号");
        return found.get(0);
    }

    /** Lock SAME operation row through original Service transaction. Duplicate confirmation returns stored receipt. */
    @Transactional
    public Receipt confirm(Long uid, String operationId, String token, String payPassword) {
        if (operationId == null || operationId.length() != 36 ||
                token == null || token.length() != 64)
            throw new ForbiddenException("确认凭证无效");
        Long campus = campuses.requireUserCampus(uid);
        Snapshot snap = row(uid, operationId, true);
        if (!sha(snap.payload()).equals(snap.payloadHash()))
            throw new BusinessException("草稿内容校验失败");
        if (!campus.equals(snap.campusId()) ||
                !MessageDigest.isEqual(sha(token).getBytes(StandardCharsets.US_ASCII),
                        snap.confirmHash().getBytes(StandardCharsets.US_ASCII)))
            throw new ForbiddenException("校区变更或确认凭证无效");
        if ("SUCCEEDED".equals(snap.status())) {
            return new Receipt(operationId, snap.status(), snap.taskId(), snap.orderId(), snap.total());
        }
        if (!"PREPARED".equals(snap.status()) || !snap.expiresAt().isAfter(now()))
            throw new BusinessException("草稿已过期或不可确认");
        if (payPassword == null || payPassword.isBlank())
            throw new BusinessException("请在 Java 确认流程输入支付密码");
        DraftInput payload;
        try { payload = mapper.readValue(snap.payload(), DraftInput.class); }
        catch (JsonProcessingException ex) { throw new BusinessException("草稿内容不可读取"); }
        Long taskId;
        Long orderId = null;
        if ("PUBLIC_TASK".equals(snap.mode())) {
            TaskPublishDTO dto = publicDto(payload.task(), payPassword);
            TaskFeeBreakdown current = fee(dto);
            if (current.total().compareTo(snap.total()) != 0 ||
                    current.serviceFee().compareTo(snap.quotedFee()) != 0)
                throw new BusinessException("草稿金额已变化，请重新预览");
            checkOwnerAddress(uid, dto.getDeliveryAddressId());
            Task task = taskService.publishTask(uid, dto);
            taskId = task.getId();
        } else if ("SERVICE_OFFER".equals(snap.mode())) {
            OfferInput input = payload.offer();
            if (input == null) throw new BusinessException("草稿内容不合法");
            checkOwnerAddress(uid, input.deliveryAddressId());
            ServiceOfferService.OfferView offer = offers.detail(uid, input.offerId());
            if (snap.quotedFee().compareTo(offer.servicePrice()) != 0)
                throw new BusinessException("服务报价已变更，请重新生成草稿");
            ServiceOfferService.BookingForm booking = new ServiceOfferService.BookingForm(
                    "agent_" + operationId.replace("-", ""), input.deliveryAddressId(),
                    input.pickupAddress(), zero(input.shoppingBudget()), input.privateNote(), payPassword);
            // Original Service performs final booking, locks offer, checks balance and debits once.
            ServiceOfferService.BookingReceipt done = offers.book(uid, input.offerId(), booking, snap.quotedFee());
            taskId = done.taskId();
            orderId = done.orderId();
            if (snap.total().compareTo(done.totalAmount()) != 0)
                throw new BusinessException("预约实际金额与草稿不一致");
        } else throw new BusinessException("草稿模式不合法");
        int updated = jdbc.update("UPDATE agent_operation SET status='SUCCEEDED',task_id=?,order_id=? " +
                "WHERE operation_id=? AND user_id=? AND status='PREPARED'",
                taskId, orderId, operationId, uid);
        if (updated != 1) throw new BusinessException("草稿状态冲突");
        return new Receipt(operationId, "SUCCEEDED", taskId, orderId, snap.total());
    }

    @Transactional(readOnly = true)
    public Receipt receipt(Long uid, String operationId) {
        Long campus = campuses.requireUserCampus(uid);
        Snapshot snap = row(uid, operationId, false);
        if (!campus.equals(snap.campusId())) throw new ForbiddenException("校区变更");
        return new Receipt(operationId, snap.status(), snap.taskId(), snap.orderId(), snap.total());
    }
}
