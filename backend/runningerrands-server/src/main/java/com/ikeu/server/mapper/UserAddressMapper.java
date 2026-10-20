package com.ikeu.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikeu.model.entity.UserAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户地址 Mapper，提供地址实体的基础 CRUD 操作。
 * @author ikeu
 * @since 2025/05/21
 */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {
}
