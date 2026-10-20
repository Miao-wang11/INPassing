package com.ikeu.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikeu.model.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper，提供操作日志实体的基础 CRUD 操作。
 * @author ikeu
 * @since 2026/06/03
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
