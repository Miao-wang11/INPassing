/**
 * Token 安全存储 — 访问令牌存模块闭包内存，刷新令牌存 sessionStorage。
 *
 * 与 localStorage 方案的区别：
 * - access token 不暴露在全局可枚举的持久化存储中，降低 XSS 窃取风险
 * - sessionStorage 在标签页关闭后自动清除，限制泄露窗口
 * - 页面刷新时从 sessionStorage 恢复内存令牌，不影响用户体验
 */

const ACCESS_KEY = 'admin_at'
const REFRESH_KEY = 'admin_rt'

let _accessToken: string | null = null

export function getAdminToken(): string | null {
  if (_accessToken) return _accessToken
  const cached = sessionStorage.getItem(ACCESS_KEY)
  if (cached) _accessToken = cached
  return _accessToken
}

export function setAdminToken(token: string): void {
  _accessToken = token
  sessionStorage.setItem(ACCESS_KEY, token)
}

export function removeAdminToken(): void {
  _accessToken = null
  sessionStorage.removeItem(ACCESS_KEY)
}

export function getAdminRefreshToken(): string | null {
  return sessionStorage.getItem(REFRESH_KEY)
}

export function setAdminRefreshToken(token: string): void {
  sessionStorage.setItem(REFRESH_KEY, token)
}

export function removeAdminRefreshToken(): void {
  sessionStorage.removeItem(REFRESH_KEY)
}
