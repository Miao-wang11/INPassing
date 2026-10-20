package com.ikeu.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统配置键值存储实体
 * @author ikeu
 * @since 2026/06/18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("system_config")
public class SystemConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 配置 ID */
    @TableId(type = IdType.AUTO)
    private Long id;


    /** 配置键 */
    @TableField("config_key")
    private String configKey;

    /** 配置值 */
    @TableField("config_value")
    private String configValue;

    /** 配置分组 */
    @TableField("config_group")
    private String configGroup;

    /** 值类型（int / decimal / string） */
    @TableField("value_type")
    private String valueType;

    /** 配置描述 */
    @TableField("description")
    private String description;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
