package com.ikeu.server.controller.user;

import com.ikeu.common.constant.MessageConstant;
import com.ikeu.common.context.BaseContext;
import com.ikeu.common.result.Result;
import com.ikeu.model.dto.AddressSaveDTO;
import com.ikeu.model.vo.AddressVO;
import com.ikeu.server.service.UserAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户地址簿 CRUD 接口，提供地址的增删改查和默认地址设置功能。
 * @author ikeu
 * @since 2025/05/21
 */
@Tag(name = "用户端-地址簿接口", description = "用户地址簿CRUD相关接口")
@RestController
@RequestMapping("/user/address")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService userAddressService;

    /**
     * 保存新的地址信息。
     *
     * <p>委托 {@link UserAddressService#saveAddressInfo} 将地址信息（联系人、手机号、
     * 省市区、详细地址、标签等）存入 user_address 表，关联到当前用户。
     *
     * @param addressSaveDTO 地址保存DTO
     * @return 操作结果
     */
    @Operation(summary = "保存地址信息")
    @PostMapping("/save")
    public Result<Void> saveAddressInfo(@Valid @RequestBody AddressSaveDTO addressSaveDTO) {
        Long userId = BaseContext.getCurrentId();
        userAddressService.saveAddressInfo(userId, addressSaveDTO);
        return Result.success(MessageConstant.ADDRESS_SAVE_SUCCESS);
    }

    /**
     * 删除指定的地址。
     *
     * <p>委托 {@link UserAddressService#deleteAddress} 校验地址归属当前用户后执行物理删除。
     *
     * @param id 地址ID
     * @return 操作结果
     */
    @Operation(summary = "删除地址")
    @DeleteMapping("/{id}")
    public Result<Void> deleteAddress(@PathVariable Long id) {
        userAddressService.deleteAddress(BaseContext.getCurrentId(), id);
        return Result.success(MessageConstant.ADDRESS_DELETE_SUCCESS);
    }

    /**
     * 修改指定地址的信息。
     *
     * <p>委托 {@link UserAddressService#updateAddress} 校验地址归属当前用户后，
     * 更新地址各字段（联系人、手机号、省市区、详细地址、标签等）。
     *
     * @param id 地址ID
     * @param addressSaveDTO 地址更新DTO
     * @return 操作结果
     */
    @Operation(summary = "修改地址")
    @PutMapping("/{id}")
    public Result<Void> updateAddress(@PathVariable Long id, @Valid @RequestBody AddressSaveDTO addressSaveDTO) {
        userAddressService.updateAddress(BaseContext.getCurrentId(), id, addressSaveDTO);
        return Result.success(MessageConstant.ADDRESS_UPDATE_SUCCESS);
    }

    /**
     * 获取当前用户的地址列表。
     *
     * <p>委托 {@link UserAddressService#listAddresses} 查询该用户所有地址，
     * 默认地址排在前面，返回 AddressVO 列表。
     *
     * @return 地址列表
     */
    @Operation(summary = "获取地址列表")
    @GetMapping("/list")
    public Result<List<AddressVO>> list() {
        List<AddressVO> list = userAddressService.listAddresses(BaseContext.getCurrentId());
        return Result.success(list);
    }

    /**
     * 将指定地址设置为默认地址。
     *
     * <p>委托 {@link UserAddressService#setDefault} 先将该用户所有地址的 is_default 清零，
     * 再将目标地址的 is_default 设为 1。
     *
     * @param id 地址ID
     * @return 操作结果
     */
    @Operation(summary = "设置默认地址")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        userAddressService.setDefault(BaseContext.getCurrentId(), id);
        return Result.success(MessageConstant.ADDRESS_SET_DEFAULT_SUCCESS);
    }
}
