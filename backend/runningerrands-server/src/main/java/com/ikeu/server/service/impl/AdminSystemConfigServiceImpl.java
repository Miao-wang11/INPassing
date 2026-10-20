package com.ikeu.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikeu.common.constant.MessageConstant;
import com.ikeu.common.exception.BusinessException;
import com.ikeu.model.dto.SystemConfigBatchUpdateDTO;
import com.ikeu.model.entity.SystemConfig;
import com.ikeu.model.vo.SystemConfigVO;
import com.ikeu.server.mapper.SystemConfigMapper;
import com.ikeu.server.service.AdminSystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置管理服务实现，支持全量查询和批量更新。
 * @author ikeu
 * @since 2026/06/18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig> implements AdminSystemConfigService {

    /**
     * 全量查询系统配置，按分组和配置键升序排列。
     *
     * @return 全部系统配置项 VO 列表
     */
    @Override
    public List<SystemConfigVO> listAll() {
        // 按分组和配置键升序查询全部配置项
        List<SystemConfig> configs = lambdaQuery()
                .orderByAsc(SystemConfig::getConfigGroup, SystemConfig::getConfigKey)
                .list();
        return configs.stream().map(c -> SystemConfigVO.builder()
                .id(c.getId())
                .configKey(c.getConfigKey())
                .configValue(c.getConfigValue())
                .configGroup(c.getConfigGroup())
                .valueType(c.getValueType())
                .description(c.getDescription())
                .updatedAt(c.getUpdatedAt())
                .build()).collect(Collectors.toList());
    }

    /**
     * 批量更新系统配置，含去重合并、值类型校验（int/decimal/string）和事务性写入。
     *
     * @param dto 包含待更新配置项列表的 DTO，支持 {@code @Valid} 级联校验
     */
    @Override
    @Transactional
    public void batchUpdate(SystemConfigBatchUpdateDTO dto) {
        // 提取所有待更新的配置键并去重
        List<String> keys = dto.getItems().stream()
                .map(SystemConfigBatchUpdateDTO.Item::getConfigKey)
                .distinct().collect(Collectors.toList());

        // 批量查询现有配置，转为 Map<configKey, SystemConfig>
        Map<String, SystemConfig> configMap = lambdaQuery()
                .in(SystemConfig::getConfigKey, keys)
                .list().stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, c -> c, (a, b) -> a));

        // 合并重复配置键，后值覆盖前值
        Map<String, String> updateMap = dto.getItems().stream()
                .collect(Collectors.toMap(
                        SystemConfigBatchUpdateDTO.Item::getConfigKey,
                        SystemConfigBatchUpdateDTO.Item::getConfigValue,
                        (a, b) -> b));

        // 逐项校验值类型并执行更新
        for (var entry : updateMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            SystemConfig config = configMap.get(key);
            if (config == null) throw new BusinessException(MessageConstant.SYSTEM_CONFIG_NOT_EXIST + " [" + key + "]");

            validateValueType(config.getValueType(), value, key);
            config.setConfigValue(value);
            config.setUpdatedAt(LocalDateTime.now());
            updateById(config);
        }
        log.info("批量更新 {} 项系统配置", dto.getItems().size());
    }

    /**
     * 校验配置值的类型合法性
     * @param valueType 值类型（int / decimal）
     * @param value     待校验的配置值
     * @param configKey 配置键（用于异常消息定位）
     * @throws BusinessException 当值类型不匹配时抛出
     */
    private void validateValueType(String valueType, String value, String configKey) {
        if (valueType == null) return;
        try {
            switch (valueType) {
                case "string" -> {}
                case "int" -> Long.parseLong(value);
                case "decimal" -> new java.math.BigDecimal(value);
                default -> throw new BusinessException(MessageConstant.CONFIG_VALUE_TYPE_UNKNOWN + " [" + valueType + "]");
            }
        } catch (NumberFormatException e) {
            throw new BusinessException("配置项 [" + configKey + "] 需要 " + valueType + " 类型的值，当前值: " + value);
        }
    }
}
