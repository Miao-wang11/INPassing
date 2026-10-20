/**
 * 用户模块 API
 * 鉴权: authentication header (登录/注册/微信登录无需)
 */
import { get, post, put, del } from '@/utils/request'

// ---------- 无需鉴权 ----------

/** 发送短信验证码，operation: login/register/change_phone/reset_password/reset_pay_password */
export function sendCode(phone, operation) {
  return post('/user/send', { phone, operation }, { auth: 'none' })
}

/** 密码 / 验证码登录 */
export function login(payload) {
  return post('/user/login', payload, { auth: 'none' })
}

/** 用户注册 */
export function register(payload) {
  return post('/user/register', payload, { auth: 'none' })
}

/** 微信小程序登录 */
export function wechatLogin(code, encryptedData, iv) {
  return post('/user/wechat/login', { code, encryptedData, iv }, { auth: 'none' })
}

// ---------- 需要鉴权 ----------

/** 获取当前用户信息 */
export function getUserInfo() {
  return get('/user/info')
}

/** 修改个人信息 */
export function updateProfile(payload) {
  return put('/user/profile', payload)
}

/** 修改手机号 */
export function updatePhone(newPhone, code) {
  return put('/user/phone', { newPhone, code })
}

/** 修改密码 */
export function updatePassword(oldPassword, newPassword) {
  return put('/user/password', { oldPassword, newPassword })
}

/** 重置登录密码（忘记密码，短信验证码验证） */
export function resetPassword(phone, code, newPassword) {
  return put('/user/password/reset', { phone, code, newPassword })
}

/** 重置支付密码（忘记支付密码，短信验证码验证） */
export function resetPayPassword(phone, code, newPassword) {
  return put('/user/pay-password/reset', { phone, code, newPassword })
}

/** 设置支付密码（首次，无需身份校验） */
export function setPayPassword(payPassword) {
  return put('/user/pay-password', { payPassword })
}

/** 修改支付密码 */
export function changePayPassword(oldPayPassword, newPayPassword) {
  return put('/user/pay-password/change', { oldPayPassword, newPayPassword })
}

/** 提交实名认证（学生身份） */
export function certify(realName, studentId, certImageUrl) {
  return post('/user/certify', { realName, studentId, certImageUrl })
}

/** 退出登录 */
export function logout() {
  return post('/user/logout')
}

/** 查询是否已设置支付密码 */
export function getPayPasswordStatus(opts = {}) {
  return get('/user/pay-password/status', {}, opts)
}

/** 查询实名认证状态 */
export function getCertifyStatus(opts = {}) {
  return get('/user/certify/status', {}, opts)
}

/** 注销账户 */
export function deleteAccount() {
  return del('/user/account')
}
