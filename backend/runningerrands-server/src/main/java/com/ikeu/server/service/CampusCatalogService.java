package com.ikeu.server.service;

import com.ikeu.common.constant.StatusConstant;
import com.ikeu.common.exception.BusinessException;
import com.ikeu.common.exception.ForbiddenException;
import com.ikeu.model.entity.Task;
import com.ikeu.model.entity.User;
import com.ikeu.server.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

/**
 * Canonical schools/campuses and trusted campus authorization.
 * user.campus is an old COLLEGE label and is NOT used as a security boundary.
 * campusId comes only from admin-approved certification, never from a task request.
 */
@Service
@RequiredArgsConstructor
public class CampusCatalogService {
    private final JdbcTemplate jdbc;
    private final UserMapper userMapper;

    public record CampusOption(Long campusId, Long schoolId, String schoolName, String campusName) {}

    public List<CampusOption> listActive() {
        return jdbc.query("SELECT c.id, s.id, s.name, c.name FROM school_campus c " +
                "JOIN school s ON s.id=c.school_id WHERE c.status=1 AND s.status=1 " +
                "ORDER BY s.name,c.name",
            (rs, idx) -> new CampusOption(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4)));
    }

    public void requireActive(Long campusId) {
        if (campusId == null) {
            throw new BusinessException("请选择学校和校区");
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_campus c JOIN school s ON s.id=c.school_id " +
                "WHERE c.id=? AND c.status=1 AND s.status=1", Integer.class, campusId);
        if (!Integer.valueOf(1).equals(count)) {
            throw new BusinessException("指定的校区不存在或已停用");
        }
    }

    /** Fail CLOSED for legacy rows that have no verified campus yet. */
    public Long requireUserCampus(Long userId) {
        if (userId == null) throw new ForbiddenException("请先登录并完成校区认证");
        User user = userMapper.selectById(userId);
        if (user == null || !Objects.equals(user.getStatus(), StatusConstant.ENABLE)
                || !Objects.equals(user.getIsCertify(), StatusConstant.CERTIFY_APPROVED)
                || user.getCampusId() == null) {
            throw new ForbiddenException("请完成包含校区的学生认证，或联系管理员绑定历史账号校区");
        }
        requireActive(user.getCampusId());
        return user.getCampusId();
    }

    public void requireSameCampus(Task task, Long userId) {
        if (task == null || task.getCampusId() == null
                || !task.getCampusId().equals(requireUserCampus(userId))) {
            throw new ForbiddenException("不可查看或承接其他校区的任务");
        }
    }

    @Transactional
    public void createSchool(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new BusinessException("学校名称必须为1到100个字符");
        }
        jdbc.update("INSERT INTO school (name,status) VALUES (?,1)", name.trim());
    }

    @Transactional
    public void createCampus(Long schoolId, String name) {
        if (schoolId == null || name == null || name.isBlank() || name.trim().length() > 100) {
            throw new BusinessException("学校或校区名称不合法");
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM school WHERE id=? AND status=1",
                Integer.class, schoolId);
        if (!Integer.valueOf(1).equals(exists)) throw new BusinessException("学校不存在或已停用");
        jdbc.update("INSERT INTO school_campus (school_id,name,status) VALUES (?,?,1)",
                schoolId, name.trim());
    }

    public List<java.util.Map<String,Object>> listSchoolsForAdmin() {
        return jdbc.queryForList("SELECT id,name,status FROM school ORDER BY id");
    }
}
