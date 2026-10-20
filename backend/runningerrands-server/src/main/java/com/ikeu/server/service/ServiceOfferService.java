package com.ikeu.server.service;

import com.ikeu.common.constant.TaskTypeConstant;
import com.ikeu.common.exception.BusinessException;
import com.ikeu.common.exception.ForbiddenException;
import com.ikeu.common.money.TaskFeeBreakdown;
import com.ikeu.common.result.PageResult;
import com.ikeu.model.dto.TaskPublishDTO;
import com.ikeu.model.entity.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Student-to-student service storefront. A booking atomically turns a service
 * offer into a provider-reserved task + existing order + prepaid mock ledger.
 * Prices and provider are resolved ONLY on the server. Never trust them in
 * booking DTOs. One MySQL transaction owns all created records.
 */
@Service
@RequiredArgsConstructor
public class ServiceOfferService {
    private final JdbcTemplate jdbc;
    private final CampusCatalogService campusCatalog;
    private final TaskService taskService;
    private final TaskOrderService orderService;

    public record OfferForm(String category, String subType, String title,
                            String description, BigDecimal servicePrice,
                            LocalDateTime availableFrom, LocalDateTime availableUntil) {}

    public record BookingForm(String requestKey, Long deliveryAddressId,
                              String pickupAddress, BigDecimal shoppingBudget,
                              String privateNote, String payPassword) {}

    public record OfferView(Long id, Long providerId, Long campusId, String nickname,
                            String category, String subType, String title,
                            String description, BigDecimal servicePrice,
                            LocalDateTime availableFrom, LocalDateTime availableUntil,
                            Integer status) {}

    public record BookingReceipt(Long offerId, Long taskId, Long orderId,
                                 BigDecimal totalAmount) {}

    private static final Set<String> ALLOWED_TYPES = TaskTypeConstant.ALL_TYPES;
    private static final BigDecimal MAX_SERVICE_PRICE = new BigDecimal("500.00");
    private static final BigDecimal MAX_GOODS_BUDGET = new BigDecimal("5000.00");

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static BigDecimal money(BigDecimal value, String field, boolean mustBePositive, BigDecimal max) {
        if (value == null) throw new BusinessException(field + "不能为空");
        final BigDecimal normalized;
        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BusinessException(field + "最多支持两位小数");
        }
        if (normalized.signum() < 0 || (mustBePositive && normalized.signum() == 0)
                || normalized.compareTo(max) > 0) {
            throw new BusinessException(field + "超出允许的金额范围");
        }
        return normalized;
    }

    private static BigDecimal budget(BigDecimal amount) {
        return money(amount == null ? BigDecimal.ZERO : amount, "预估商品预算", false, MAX_GOODS_BUDGET);
    }

    private static void validateOffer(OfferForm form) {
        if (form == null || !ALLOWED_TYPES.contains(form.category())) {
            throw new BusinessException("服务类别不合法");
        }
        if (form.subType() != null && (!TaskTypeConstant.ALL_SUB_TYPES.contains(form.subType()))) {
            throw new BusinessException("服务子类别不合法");
        }
        if (trimmed(form.title()).isEmpty() || trimmed(form.title()).length() > 80
                || trimmed(form.description()).isEmpty() || trimmed(form.description()).length() > 500) {
            throw new BusinessException("标题或服务介绍长度不合法");
        }
        money(form.servicePrice(), "服务报价", true, MAX_SERVICE_PRICE);
        if (form.availableFrom() == null || form.availableUntil() == null
                || !form.availableUntil().isAfter(form.availableFrom())
                || !form.availableUntil().isAfter(LocalDateTime.now())
                || form.availableUntil().isAfter(LocalDateTime.now().plusDays(30))) {
            throw new BusinessException("服务可提供时间无效或超过30天");
        }
    }

    @Transactional
    public Long publish(Long providerId, OfferForm form) {
        Long campusId = campusCatalog.requireUserCampus(providerId);
        validateOffer(form);
        jdbc.update("INSERT INTO service_offer " +
                    "(provider_id,campus_id,category,sub_type,title,description,service_price,available_from,available_until,status)" +
                    " VALUES (?,?,?,?,?,?,?,?,?,1)",
                providerId, campusId, form.category(), form.subType(), trimmed(form.title()),
                trimmed(form.description()), money(form.servicePrice(), "服务报价", true, MAX_SERVICE_PRICE),
                form.availableFrom(), form.availableUntil());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** Editing an offer changes FUTURE quotes; existing booked task and ledger are immutable. */
    @Transactional
    public void edit(Long ownerId, Long offerId, OfferForm form) {
        Long campusId = campusCatalog.requireUserCampus(ownerId);
        validateOffer(form);
        int n = jdbc.update("UPDATE service_offer SET category=?,sub_type=?,title=?,description=?,service_price=?," +
                        "available_from=?,available_until=? WHERE id=? AND provider_id=? AND campus_id=?",
                form.category(), form.subType(), trimmed(form.title()), trimmed(form.description()),
                money(form.servicePrice(), "服务报价", true, MAX_SERVICE_PRICE),
                form.availableFrom(), form.availableUntil(), offerId, ownerId, campusId);
        if (n != 1) throw new ForbiddenException("只能修改自己所属校区的服务");
    }

    @Transactional
    public void changeStatus(Long ownerId, Long offerId, int status) {
        if (status != 0 && status != 1) throw new BusinessException("服务状态只能为0或1");
        Long campusId = campusCatalog.requireUserCampus(ownerId);
        int n = jdbc.update("UPDATE service_offer SET status=? WHERE id=? AND provider_id=? AND campus_id=?",
                status, offerId, ownerId, campusId);
        if (n != 1) throw new ForbiddenException("只能管理自己所属校区的服务");
    }

    private static final String SELECT_OFFER =
            "SELECT o.id,o.provider_id,o.campus_id,u.nickname,o.category,o.sub_type,o.title," +
            "o.description,o.service_price,o.available_from,o.available_until,o.status " +
            "FROM service_offer o JOIN user u ON u.id=o.provider_id ";

    private static OfferView map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new OfferView(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4),
                rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),
                rs.getBigDecimal(9),rs.getObject(10, LocalDateTime.class),
                rs.getObject(11, LocalDateTime.class),rs.getInt(12));
    }

    public PageResult<OfferView> hall(Long studentId, String category, int page, int size) {
        Long campusId = campusCatalog.requireUserCampus(studentId);
        if (page < 1 || size < 1 || size > 100) throw new BusinessException("分页参数无效");
        if (category != null && !category.isBlank() && !ALLOWED_TYPES.contains(category)) {
            throw new BusinessException("服务类别不合法");
        }
        boolean filter = category != null && !category.isBlank();
        String condition = " WHERE o.campus_id=? AND o.status=1 AND u.status=1 AND u.is_certify=2 " +
                "AND u.campus_id=o.campus_id AND o.available_until>NOW()" +
                (filter ? " AND o.category=?" : "");
        Object[] args = filter ? new Object[]{campusId,category} : new Object[]{campusId};
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM service_offer o JOIN user u ON u.id=o.provider_id " +
                condition, Long.class, args);
        String sql = SELECT_OFFER + condition + " ORDER BY o.created_at DESC, o.id DESC LIMIT ? OFFSET ?";
        Object[] pageArgs = filter ? new Object[]{campusId,category,size,(page-1)*size}
                : new Object[]{campusId,size,(page-1)*size};
        return new PageResult<>(count == null ? 0L : count, jdbc.query(sql, ServiceOfferService::map, pageArgs));
    }

    public List<OfferView> mine(Long providerId) {
        Long campus = campusCatalog.requireUserCampus(providerId);
        return jdbc.query(SELECT_OFFER + "WHERE o.provider_id=? AND o.campus_id=? ORDER BY o.id DESC",
                ServiceOfferService::map, providerId, campus);
    }

    public OfferView detail(Long studentId, Long offerId) {
        Long campus = campusCatalog.requireUserCampus(studentId);
        List<OfferView> rows = jdbc.query(SELECT_OFFER +
                "WHERE o.id=? AND o.campus_id=? AND o.status=1 AND o.available_until>NOW() " +
                "AND u.status=1 AND u.is_certify=2 AND u.campus_id=o.campus_id",
                ServiceOfferService::map, offerId, campus);
        if (rows.isEmpty()) throw new ForbiddenException("服务不存在或不可在本校区查看");
        return rows.get(0);
    }

    private OfferView lockedOffer(Long id) {
        List<OfferView> rows = jdbc.query(SELECT_OFFER + "WHERE o.id=? FOR UPDATE",
                ServiceOfferService::map, id);
        if (rows.isEmpty()) throw new BusinessException("服务不存在");
        return rows.get(0);
    }

    private static String fingerprint(Long offerId, BookingForm form, BigDecimal goodsBudget) {
        String canonical = offerId + "|" + form.deliveryAddressId() + "|" +
                trimmed(form.pickupAddress()) + "|" + goodsBudget.toPlainString() + "|" +
                trimmed(form.privateNote());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private BookingReceipt existing(Long requesterId, String requestKey, Long offerId, String hash) {
        List<BookingReceipt> result = jdbc.query(
                "SELECT offer_id,task_id,order_id,total_amount,request_hash FROM service_booking " +
                        "WHERE requester_id=? AND request_key=?",
                (rs,n)->{
                    if (rs.getLong(1) != offerId || !Objects.equals(rs.getString(5), hash)) {
                        throw new BusinessException("同一请求号不能提交不同委托内容");
                    }
                    Long taskId=rs.getObject(2)==null?null:rs.getLong(2);
                    Long orderId=rs.getObject(3)==null?null:rs.getLong(3);
                    if (taskId == null || orderId == null) {
                        throw new BusinessException("委托正在处理中，请勿重复提交");
                    }
                    return new BookingReceipt(rs.getLong(1),taskId,orderId,rs.getBigDecimal(4));
                }, requesterId, requestKey);
        return result.isEmpty() ? null : result.get(0);
    }

    @Transactional
    public BookingReceipt book(Long requesterId, Long offerId, BookingForm form) {
        Long campusId = campusCatalog.requireUserCampus(requesterId);
        if (offerId == null || form == null || form.requestKey() == null
                || !form.requestKey().matches("[A-Za-z0-9_-]{8,64}")
                || form.deliveryAddressId() == null || form.deliveryAddressId() <= 0
                || trimmed(form.pickupAddress()).isEmpty() || trimmed(form.pickupAddress()).length() > 255
                || form.privateNote()!=null && form.privateNote().length()>256
                || trimmed(form.payPassword()).isEmpty()) {
            throw new BusinessException("委托信息或请求号不合法");
        }
        BigDecimal goods = budget(form.shoppingBudget());
        String hash = fingerprint(offerId,form,goods);
        BookingReceipt previous = existing(requesterId,form.requestKey(),offerId,hash);
        if (previous != null) return previous;

        // Lock the offer row so status/price cannot change during a booking.
        OfferView offer = lockedOffer(offerId);
        if (!campusId.equals(offer.campusId()) ||
                !campusId.equals(campusCatalog.requireUserCampus(offer.providerId()))) {
            throw new ForbiddenException("不能跨校区委托服务");
        }
        if (offer.providerId().equals(requesterId)) {
            throw new ForbiddenException("不能委托自己的服务");
        }
        if (offer.status()!=1 || LocalDateTime.now().isBefore(offer.availableFrom())
                || !LocalDateTime.now().isBefore(offer.availableUntil())) {
            throw new BusinessException("服务当前不在可预约时段");
        }

        BigDecimal fee = money(offer.servicePrice(),"服务报价",true,MAX_SERVICE_PRICE);
        BigDecimal total = fee.add(goods);
        try {
            TaskFeeBreakdown.of(fee,BigDecimal.ZERO,goods,total);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("费用分项校验失败");
        }
        // INSERT IGNORE waits for concurrently in-flight unique keys. If it
        // loses, return the committed existing order without another debit.
        int inserted = jdbc.update("INSERT IGNORE INTO service_booking " +
                "(offer_id,requester_id,request_key,request_hash,total_amount) VALUES (?,?,?,?,?)",
                offerId, requesterId, form.requestKey(), hash, total);
        if (inserted == 0) {
            BookingReceipt replay = existing(requesterId,form.requestKey(),offerId,hash);
            if (replay == null) throw new BusinessException("委托请求号存在冲突");
            return replay;
        }

        TaskPublishDTO dto = new TaskPublishDTO();
        dto.setType(offer.category());
        dto.setSubType(offer.subType());
        dto.setPublicDesc("预约服务：" + offer.title());
        dto.setPrivateNote(form.privateNote());
        dto.setPickupAddress(trimmed(form.pickupAddress()));
        dto.setDeliveryAddressId(form.deliveryAddressId());
        dto.setTip(fee);
        dto.setDeliveryFee(BigDecimal.ZERO);
        dto.setProductCost(goods);
        dto.setExpireMinutes(60);
        dto.setPayPassword(form.payPassword());

        // Spring proxy -> same transaction. Failure of acceptance rolls back
        // booking + prepayment + task + runner profile/capacity together.
        Task task = taskService.publishTask(requesterId,dto);
        jdbc.update("UPDATE task SET service_offer_id=?,reserved_runner_id=? WHERE id=?",
                offerId, offer.providerId(), task.getId());
        Long orderId = orderService.acceptOrder(offer.providerId(),task.getId());
        jdbc.update("UPDATE service_booking SET task_id=?,order_id=? " +
                "WHERE requester_id=? AND request_key=?",
                task.getId(),orderId,requesterId,form.requestKey());
        return new BookingReceipt(offerId,task.getId(),orderId,total);
    }
}
