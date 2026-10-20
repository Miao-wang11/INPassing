package com.ikeu.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikeu.model.entity.PaymentIdempotent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付幂等 Mapper，提供支付幂等记录的基础 CRUD 操作。
 * @author ikeu
 * @since 2025/05/21
 */
@Mapper
public interface PaymentIdempotentMapper extends BaseMapper<PaymentIdempotent> {
}
