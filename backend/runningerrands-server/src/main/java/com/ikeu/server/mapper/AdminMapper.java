package com.ikeu.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikeu.model.entity.Admin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员 Mapper，提供管理员实体的基础 CRUD 操作。
 * @author ikeu
 * @since 2025/05/21
 */
@Mapper
public interface AdminMapper extends BaseMapper<Admin> {
}