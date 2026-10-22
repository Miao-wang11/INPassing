package com.ikeu.server.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ikeu.common.constant.CreditConstant;
import com.ikeu.common.constant.StatusConstant;
import com.ikeu.common.exception.ForbiddenException;
import com.ikeu.common.result.PageResult;
import com.ikeu.model.entity.RunnerProfile;
import com.ikeu.model.entity.Task;
import com.ikeu.model.entity.User;
import com.ikeu.model.vo.TaskListVO;
import com.ikeu.server.mapper.RunnerProfileMapper;
import com.ikeu.server.mapper.TaskMapper;
import com.ikeu.server.mapper.UserMapper;
import com.ikeu.server.service.CampusCatalogService;
import com.ikeu.server.service.ServiceOfferService;
import com.ikeu.server.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Explicit allowlisted projections; never serialize original TaskListVO or OfferView to the model. */
@Service
@RequiredArgsConstructor
public class AgentReadOnlyService {
    private final CampusCatalogService campuses;
    private final TaskService tasks;
    private final ServiceOfferService offers;
    private final RunnerProfileMapper runnerProfiles;
    private final TaskMapper taskMapper;
    private final UserMapper users;

    public record TaskCard(Long taskId, String type, String subType, String publicDesc,
                           String pickupArea, String deliveryArea, BigDecimal reward,
                           BigDecimal tip, BigDecimal deliveryFee, BigDecimal productCost,
                           LocalDateTime expireTime) {}
    public record OfferCard(Long offerId, String category, String subType, String title,
                            String description, BigDecimal servicePrice,
                            LocalDateTime availableFrom, LocalDateTime availableUntil) {}
    public record Eligibility(boolean canAttempt, String reason) {}
    public record CampusPlace(Long campusId, String campusName, Long schoolId, String schoolName,
                              boolean coordinatesAvailable) {}

    /** Untrusted text can include personally identifying information; only coarse public location is exposed. */
    static String safeText(String s) {
        if (s == null) return "";
        String value = s.replaceAll("1[3-9]\\d{9}", "[电话已隐藏]")
                .replaceAll("(?<!\\d)\\d{3,4}(?:室|房|号)(?!\\d)", "[房间已隐藏]");
        return value.length() > 256 ? value.substring(0, 256) : value;
    }
    static String coarseArea(String s) {
        if (s == null) return "";
        String value = s.replaceAll("(?s)\\d+层.*$", "")
                .replaceAll("(?<!\\d)\\d{3,4}(?:室|房|号)?(?!\\d).*$", "")
                .replaceAll("1[3-9]\\d{9}.*$", "").trim();
        return safeText(value);
    }
    private TaskCard toTask(TaskListVO t, Long campusId) {
        if (!campusId.equals(t.getCampusId()) || !Objects.equals(t.getStatus(), StatusConstant.TASK_WAITING)) {
            throw new ForbiddenException("候选任务校区或状态不合法");
        }
        return new TaskCard(t.getTaskId(), t.getType(), t.getSubType(),
                safeText(t.getPublicDesc()), coarseArea(t.getPickupAddress()),
                coarseArea(t.getDeliveryAddress()), t.getReward(), t.getTip(),
                t.getDeliveryFee(), t.getProductCost(), t.getExpireTime());
    }
    private PageResult<TaskCard> taskPage(Long uid, PageResult<TaskListVO> result) {
        Long campusId = campuses.requireUserCampus(uid);
        return new PageResult<>(result.getTotal(), result.getRecords().stream()
                .filter(t -> t.getPublisherId() == null || !uid.equals(t.getPublisherId()))
                .map(t -> toTask(t, campusId)).toList());
    }


    /** Only verified, public, currently eligible candidate cards. Read-only precheck, NOT a claim. */
    public record CandidateBatch(long examined, long totalBeforeEligibility, boolean morePages,
                                 List<TaskCard> records) {}

    @Transactional(readOnly = true)
    public CandidateBatch matchingCandidates(Long uid, int page, int size) {
        return matchingCandidates(uid, page, size, null, null, null, null);
    }

    /** SQL-pushed filters, bounded pages and per-candidate eligibility; no whole-table load. */
    @Transactional(readOnly = true)
    public CandidateBatch matchingCandidates(Long uid, int page, int size,
                                             String keyword, String type, String pickup, String delivery) {
        Long campusId = campuses.requireUserCampus(uid);
        PageResult<TaskListVO> raw = (pickup != null && !pickup.isBlank())
                || (delivery != null && !delivery.isBlank())
                ? tasks.filterTasks(type, pickup, delivery, null, null, null, page, size)
                : tasks.searchTasks(keyword, type, null, null, page, size);
        List<TaskCard> result = new java.util.ArrayList<>();
        for (TaskListVO t : raw.getRecords()) {
            if (!campusId.equals(t.getCampusId()))
                throw new ForbiddenException("候选任务校区不合法");
            if (uid.equals(t.getPublisherId()) ||
                    !Objects.equals(t.getStatus(), StatusConstant.TASK_WAITING) ||
                    t.getExpireTime() == null || !t.getExpireTime().isAfter(LocalDateTime.now())) {
                continue;
            }
            try {
                // Uses existing runner profile, certification, sex, capacity, ban/credit checks.
                // Never invokes ensureForAccept(), nor any task/order mutation.
                if (eligibility(uid, t.getTaskId()).canAttempt()) {
                    result.add(toTask(t, campusId));
                }
            } catch (ForbiddenException ex) {
                // Task may have become private/claimed between hall query and precheck.
                // It must disappear from recommendations. Final acceptOrder rechecks atomically.
            }
        }
        long total = raw.getTotal();
        return new CandidateBatch(raw.getRecords().size(), total, (long) page * size < total,
                List.copyOf(result));
    }

    @Transactional(readOnly = true)
    public PageResult<TaskCard> searchTasks(Long uid, String keyword, String type, int page, int size) {
        campuses.requireUserCampus(uid);
        return taskPage(uid, tasks.searchTasks(keyword, type, null, null, page, size));
    }
    @Transactional(readOnly = true)
    public PageResult<TaskCard> filterTasks(Long uid, String type, String pickup, String delivery, int page, int size) {
        campuses.requireUserCampus(uid);
        return taskPage(uid, tasks.filterTasks(type, pickup, delivery, null, null, null, page, size));
    }

    @Transactional(readOnly = true)
    public PageResult<OfferCard> offers(Long uid, String category, int page, int size) {
        Long campusId = campuses.requireUserCampus(uid);
        PageResult<ServiceOfferService.OfferView> raw = offers.hall(uid, category, page, size);
        List<OfferCard> cards = raw.getRecords().stream()
                .filter(v -> !uid.equals(v.providerId())).map(v -> {
            if (!campusId.equals(v.campusId()) || v.status() != 1 || uid.equals(v.providerId())) {
                throw new ForbiddenException("服务校区或状态不合法");
            }
            return new OfferCard(v.id(), v.category(), v.subType(), safeText(v.title()),
                    safeText(v.description()), v.servicePrice(), v.availableFrom(), v.availableUntil());
        }).toList();
        return new PageResult<>(raw.getTotal(), cards);
    }

    @Transactional(readOnly = true)
    public List<CampusPlace> campus(Long uid) {
        Long id = campuses.requireUserCampus(uid);
        return campuses.listActive().stream().filter(c -> c.campusId().equals(id))
                .map(c -> new CampusPlace(c.campusId(), c.campusName(), c.schoolId(), c.schoolName(), false))
                .toList();
    }


    /** Detailed PUBLIC candidate only. Do not expose private notes, pickup codes or direct contacts. */
    @Transactional(readOnly = true)
    public TaskCard taskDetail(Long uid, Long taskId) {
        Long campusId = campuses.requireUserCampus(uid);
        Task t = taskMapper.selectById(taskId);
        if (t == null || !campusId.equals(t.getCampusId()) || t.getReservedRunnerId() != null
                || uid.equals(t.getPublisherId())
                || !Objects.equals(t.getStatus(), StatusConstant.TASK_WAITING)
                || t.getExpireTime() == null || !t.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new ForbiddenException("公开任务不存在或已失效");
        }
        return new TaskCard(t.getId(), t.getType(), t.getSubType(), safeText(t.getPublicDesc()),
                coarseArea(t.getPickupAddress()), coarseArea(t.getDeliveryAddress()),
                t.getReward(), t.getTip(), t.getDeliveryFee(), t.getProductCost(), t.getExpireTime());
    }

    @Transactional(readOnly = true)
    public OfferCard offerDetail(Long uid, Long offerId) {
        Long campusId = campuses.requireUserCampus(uid);
        ServiceOfferService.OfferView v = offers.detail(uid, offerId);
        if (!campusId.equals(v.campusId()) || v.status() != 1 || uid.equals(v.providerId())) {
            throw new ForbiddenException("服务不存在或已失效");
        }
        return new OfferCard(v.id(), v.category(), v.subType(), safeText(v.title()),
                safeText(v.description()), v.servicePrice(), v.availableFrom(), v.availableUntil());
    }

    /** A read-only preview. Not a reservation: acceptOrder rechecks and may create a profile. */
    @Transactional(readOnly = true)
    public Eligibility eligibility(Long uid, Long taskId) {
        Long campusId = campuses.requireUserCampus(uid);
        Task task = taskMapper.selectById(taskId);
        if (task == null || !campusId.equals(task.getCampusId()) || task.getReservedRunnerId() != null) {
            throw new ForbiddenException("任务不存在或不属于公开的本校区候选");
        }
        if (!Objects.equals(task.getStatus(), StatusConstant.TASK_WAITING) ||
                task.getExpireTime() == null || !task.getExpireTime().isAfter(LocalDateTime.now())) {
            return new Eligibility(false, "TASK_UNAVAILABLE");
        }
        if (uid.equals(task.getPublisherId())) return new Eligibility(false, "OWN_TASK");
        User user = users.selectById(uid);
        if (user == null || !Objects.equals(user.getStatus(), StatusConstant.ENABLE)
                || !Objects.equals(user.getIsCertify(), StatusConstant.CERTIFY_APPROVED)) {
            return new Eligibility(false, "STUDENT_UNAVAILABLE");
        }
        if (task.getRequireSex() != null && !task.getRequireSex().isBlank()
                && !"不限".equals(task.getRequireSex()) && !task.getRequireSex().equals(user.getSex())) {
            return new Eligibility(false, "GENDER_RESTRICTED");
        }
        RunnerProfile runner = runnerProfiles.selectOne(
                new LambdaQueryWrapper<RunnerProfile>().eq(RunnerProfile::getUserId, uid));
        // On FIRST explicit accept the existing Java Service will create a certified student's profile.
        if (runner == null) return new Eligibility(true, "PROFILE_CREATED_ONLY_ON_ACCEPT");
        if (!Objects.equals(runner.getVerifyStatus(), StatusConstant.CERTIFY_APPROVED)) {
            return new Eligibility(false, "PROFILE_UNVERIFIED");
        }
        if (!Objects.equals(runner.getIsOnline(), StatusConstant.RUNNER_ONLINE)) {
            return new Eligibility(false, "OFFLINE");
        }
        if (Objects.equals(runner.getIsBanned(), 1)) return new Eligibility(false, "BANNED");
        if (runner.getCreditScore() != null && runner.getCreditScore() < CreditConstant.CREDIT_FREEZE_THRESHOLD) {
            return new Eligibility(false, "LOW_CREDIT");
        }
        if (runner.getCurrentOrders() == null || runner.getMaxConcurrentOrders() == null ||
                runner.getCurrentOrders() >= runner.getMaxConcurrentOrders()) {
            return new Eligibility(false, "CAPACITY_LIMIT");
        }
        return new Eligibility(true, "PREVIEW_ONLY");
    }
}
