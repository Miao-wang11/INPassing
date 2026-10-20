package com.ikeu.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikeu.common.constant.MessageConstant;
import com.ikeu.common.exception.NotFoundException;
import com.ikeu.model.dto.AddressSaveDTO;
import com.ikeu.model.entity.UserAddress;
import com.ikeu.model.vo.AddressVO;
import com.ikeu.server.mapper.UserAddressMapper;
import com.ikeu.server.service.UserAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户地址服务实现，处理地址的新增、删除、修改、查询和默认地址设置。
 * @author ikeu
 * @since 2026/05/14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAddressServiceImpl extends ServiceImpl<UserAddressMapper, UserAddress> implements UserAddressService {

    /**
     * 新增地址，若设置为默认地址则先清除其他默认标记。
     *
     * <p>校验逻辑：构建 UserAddress 实体 → 如果 isDefault=1，先调用 clearDefaultAddress
     * 将该用户所有地址的 isDefault 清零 → 持久化新地址。
     *
     * @param userId 用户ID
     * @param addressSaveDTO 新增地址DTO
     */
    @Override
    @Transactional
    public void saveAddressInfo(Long userId, AddressSaveDTO addressSaveDTO) {
        UserAddress address = UserAddress.builder()
                .userId(userId)
                .contactName(addressSaveDTO.getContactName())
                .contactPhone(addressSaveDTO.getContactPhone())
                .sex(addressSaveDTO.getSex())
                .detail(addressSaveDTO.getDetail())
                .lng(addressSaveDTO.getLng())
                .lat(addressSaveDTO.getLat())
                .isDefault(addressSaveDTO.getIsDefault() != null ? addressSaveDTO.getIsDefault() : 0)
                .build();

        if (address.getIsDefault() == 1) {
            clearDefaultAddress(userId);
        }
        save(address);
        log.info("用户 {} 添加地址 ID: {}", userId, address.getId());
    }

    /**
     * 删除地址，校验地址存在且属于当前用户后物理删除。
     *
     * @param userId 用户ID
     * @param addressId 地址ID
     */
    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        UserAddress address = getById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new NotFoundException(MessageConstant.ADDRESS_NOT_EXISTS);
        }
        removeById(addressId);
        log.info("用户 {} 删除地址 ID: {}", userId, addressId);
    }

    /**
     * 修改地址信息，如设为默认则先清除其他默认地址。
     *
     * <p>校验地址存在且属于当前用户 → 更新各字段 → 如果 isDefault=1 则清除其他默认后设为1。
     *
     * @param userId 用户ID
     * @param addressId 地址ID
     * @param addressSaveDTO 修改地址DTO
     */
    @Override
    @Transactional
    public void updateAddress(Long userId, Long addressId, AddressSaveDTO addressSaveDTO) {
        UserAddress address = getById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new NotFoundException(MessageConstant.ADDRESS_NOT_EXISTS);
        }

        address.setContactName(addressSaveDTO.getContactName());
        address.setContactPhone(addressSaveDTO.getContactPhone());
        address.setSex(addressSaveDTO.getSex());
        address.setDetail(addressSaveDTO.getDetail());
        address.setLng(addressSaveDTO.getLng());
        address.setLat(addressSaveDTO.getLat());

        if (addressSaveDTO.getIsDefault() != null && addressSaveDTO.getIsDefault() == 1) {
            clearDefaultAddress(userId);
            address.setIsDefault(1);
        } else {
            address.setIsDefault(addressSaveDTO.getIsDefault() != null ? addressSaveDTO.getIsDefault() : 0);
        }

        updateById(address);
        log.info("用户 {} 修改地址 ID: {}", userId, addressId);
    }

    /**
     * 查询用户所有地址，默认地址优先，按ID倒序排列。
     *
     * @param userId 用户ID
     * @return 地址VO列表
     */
    @Override
    public List<AddressVO> listAddresses(Long userId) {
        List<UserAddress> addresses = lambdaQuery()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getId)
                .list();
        return BeanUtil.copyToList(addresses, AddressVO.class);
    }

    /**
     * 设置默认地址，先清除所有默认标记再将目标地址设为默认。
     *
     * <p>校验地址存在且属于当前用户 → clearDefaultAddress → 设 isDefault=1。
     *
     * @param userId 用户ID
     * @param addressId 地址ID
     */
    @Override
    @Transactional
    public void setDefault(Long userId, Long addressId) {
        UserAddress address = getById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new NotFoundException(MessageConstant.ADDRESS_NOT_EXISTS);
        }
        clearDefaultAddress(userId);
        address.setIsDefault(1);
        updateById(address);
        log.info("用户 {} 设置默认地址 ID: {}", userId, addressId);
    }

    /**
     * 清除用户所有地址的默认标记，查询所有 isDefault=1 的地址批量设为0。
     *
     * @param userId 用户ID
     */
    private void clearDefaultAddress(Long userId) {
        List<UserAddress> defaultAddresses = lambdaQuery()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1)
                .list();
        for (UserAddress addr : defaultAddresses) {
            addr.setIsDefault(0);
        }
        if (!defaultAddresses.isEmpty()) {
            updateBatchById(defaultAddresses);
        }
    }
}
