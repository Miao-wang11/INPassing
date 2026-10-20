package com.ikeu.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikeu.model.entity.TaskImage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务图片 Mapper，提供任务图片实体的基础 CRUD 操作。
 * @author ikeu
 * @since 2025/05/21
 */
@Mapper
public interface TaskImageMapper extends BaseMapper<TaskImage> {
}
