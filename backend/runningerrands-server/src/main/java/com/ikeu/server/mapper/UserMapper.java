package com.ikeu.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikeu.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper，提供用户实体的 CRUD 及行级锁查询。
 * @author ikeu
 * @since 2025/05/21
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 使用 SELECT ... FOR UPDATE 行级锁查询用户，防止余额并发丢失更新。
     *
     * @param id 用户ID
     * @return 用户实体，不存在返回 null
     */
    @Select("SELECT * FROM user WHERE id = #{id} FOR UPDATE")
    User selectByIdForUpdate(@Param("id") Long id);

    /**
     * 分页查询用户列表，支持按状态、认证状态筛选和关键词模糊搜索。
     *
     * <p>关键词匹配用户名（username）、手机号（phone）、昵称（nickname），
     * 筛选条件均可选，为 null 时跳过对应 WHERE 条件。按创建时间倒序排列。
     *
     * @param page      分页对象
     * @param status    用户状态（0-禁用 1-正常），可为 null
     * @param isCertify 认证状态（0-未认证 1-审核中 2-已认证 3-驳回），可为 null
     * @param keyword   搜索关键词，可为 null
     * @return 分页用户列表
     */
    Page<User> selectUsersWithKeyword(Page<User> page,
                                      @Param("status") Integer status,
                                      @Param("isCertify") Integer isCertify,
                                      @Param("keyword") String keyword);
}
