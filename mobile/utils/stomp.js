/**
 * 轻量级 STOMP 1.2 客户端 — 基于 uni.connectSocket
 *
 * 使用方式:
 *   import { StompClient } from '@/utils/stomp'
 *   const client = new StompClient()
 *   await client.connect('ws://localhost:8080/ws/chat', token)
 *   client.subscribe('/user/queue/chat', (body, headers) => { ... })
 *   client.send('/app/chat.send', {}, JSON.stringify({ receiverId: 5, content: 'hi' }))
 *   client.disconnect()
 */

const HEARTBEAT = { outgoing: 10000, incoming: 10000 }

export class StompClient {
  constructor() {
    this._socket = null
    this._connected = false
    this._subId = 0
    this._subscriptions = {}  // id → { destination, callback }
    this._pendingFrames = []   // frames queued before CONNECTED
    this._heartbeatTimer = null
    this._connectResolve = null
    this._connectReject = null
  }

  get connected() {
    return this._connected
  }

  /**
   * 建立 WebSocket 连接并发送 STOMP CONNECT 帧
   * @param {string} url      - WebSocket 地址 (e.g. ws://host/ws/chat)
   * @param {string} token    - 认证 token
   * @param {number} [timeout=10000]
   */
  connect(url, token, timeout = 10000) {
    return new Promise((resolve, reject) => {
      this._connectResolve = resolve
      this._connectReject = reject

      let settled = false
      const finish = (err, result) => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        this._connectResolve = null
        this._connectReject = null
        if (err) reject(err)
        else resolve(result)
      }

      const timer = setTimeout(() => {
        finish(new Error('STOMP 连接超时'))
      }, timeout)

      this._socket = uni.connectSocket({
        url,
        success: () => { /* socket task created */ },
        fail: (err) => {
          finish(err)
        }
      })

      this._socket.onOpen(() => {
        // 从 URL 提取 host（token 已作为查询参数附加在 URL 上）
        let host = 'localhost'
        try { const u = new URL(url); host = u.host } catch (e) { /* keep default */ }
        const frame = this._buildFrame('CONNECT', {
          'accept-version': '1.2',
          'host': host,
          'heart-beat': `${HEARTBEAT.outgoing},${HEARTBEAT.incoming}`
        })
        this._sendRaw(frame)
      })

      this._socket.onMessage((res) => {
        const data = typeof res.data === 'string' ? res.data : (res.data?.text || '')
        this._onFrame(data, () => finish(null))
      })

      this._socket.onError((err) => {
        finish(err)
      })

      this._socket.onClose(() => {
        this._connected = false
        this._stopHeartbeat()
        if (!settled) {
          finish(new Error('WebSocket 连接已关闭'))
        }
      })
    })
  }

  /**
   * 订阅目标
   * @param {string}   destination
   * @param {function} callback(body, headers)
   * @returns {string} 订阅 ID
   */
  subscribe(destination, callback) {
    const id = `sub-${++this._subId}`
    const frame = this._buildFrame('SUBSCRIBE', {
      id,
      destination,
      'ack': 'auto'
    })

    if (this._connected) {
      this._sendRaw(frame)
    } else {
      this._pendingFrames.push(frame)
    }

    this._subscriptions[id] = { destination, callback }
    return id
  }

  /**
   * 取消订阅
   */
  unsubscribe(id) {
    if (!this._subscriptions[id]) return
    const frame = this._buildFrame('UNSUBSCRIBE', { id })
    this._sendRaw(frame)
    delete this._subscriptions[id]
  }

  /**
   * 发送消息
   * @param {string} destination - e.g. /app/chat.send
   * @param {object} headers
   * @param {string} body
   */
  send(destination, headers = {}, body = '') {
    const frame = this._buildFrame('SEND', {
      destination,
      'content-type': 'application/json',
      ...headers
    }, body)
    this._sendRaw(frame)
  }

  /** 断开连接 */
  disconnect() {
    if (this._socket) {
      try {
        const frame = this._buildFrame('DISCONNECT', {
          'receipt': 'disconnect-' + Date.now()
        })
        this._sendRaw(frame)
      } catch (e) { /* ignore */ }
      this._stopHeartbeat()
      try {
        this._socket.close({
          code: 1000,
          success: () => {},
          fail: () => {}
        })
      } catch (e) {
        try { this._socket.close() } catch (_) { /* ignore */ }
      }
      this._socket = null
    }
    this._connected = false
    this._subscriptions = {}
    this._pendingFrames = []
  }

  // ---------- private ----------

  _buildFrame(command, headers = {}, body = '') {
    let frame = command + '\n'
    for (const [k, v] of Object.entries(headers)) {
      frame += `${k}:${v}\n`
    }
    frame += '\n' + body + '\x00'
    return frame
  }

  _sendRaw(data) {
    if (!this._socket) {
      console.warn('[StompClient] send skipped: socket is null')
      return
    }
    if (!this._connected && !data.startsWith('CONNECT') && data !== '\n') {
      console.warn('[StompClient] send skipped: not connected')
      return
    }
    try {
      this._socket.send({ data })
    } catch (e) {
      console.error('[StompClient] send error:', e)
    }
  }

  _onFrame(raw, onConnected) {
    // STOMP frames are separated by null byte
    const frames = raw.split('\x00').filter(Boolean)
    for (const rawFrame of frames) {
      const lines = rawFrame.trim().split('\n')
      const command = lines[0]?.trim()

      if (command === 'CONNECTED') {
        this._connected = true
        this._startHeartbeat()
        // 发送排队帧
        for (const f of this._pendingFrames) {
          this._sendRaw(f)
        }
        this._pendingFrames = []
        if (onConnected) onConnected()
        if (this._connectResolve) {
          this._connectResolve()
          this._connectResolve = null
        }
        continue
      }

      if (command === 'MESSAGE') {
        // 解析 headers 和 body
        let i = 1
        const headers = {}
        for (; i < lines.length; i++) {
          const line = lines[i]
          if (line === '') { i++; break }
          const colonIdx = line.indexOf(':')
          if (colonIdx > 0) {
            headers[line.substring(0, colonIdx)] = line.substring(colonIdx + 1)
          }
        }
        const body = lines.slice(i).join('\n')
        const subId = headers['subscription']
        if (subId && this._subscriptions[subId]) {
          try {
            this._subscriptions[subId].callback(body, headers)
          } catch (e) {
            console.error('STOMP callback error:', e)
          }
        }
        continue
      }

      if (command === 'ERROR') {
        console.error('STOMP ERROR:', rawFrame)
        // 解析 ERROR 帧的消息头
        const errMsg = this._parseErrorMessage(rawFrame)
        const err = new Error(errMsg || 'STOMP ERROR')
        err.isAuthError = /token|JWT|expired|auth|401|unauthorized/i.test(errMsg || rawFrame)
        // 如果是握手阶段的 ERROR，拒绝连接
        if (!this._connected && this._connectReject) {
          this._connectReject(err)
          this._connectReject = null
          this._connectResolve = null
        }
      }
    }
  }

  _startHeartbeat() {
    this._stopHeartbeat()
    this._heartbeatTimer = setInterval(() => {
      if (this._socket && this._connected) {
        this._sendRaw('\n')
      }
    }, HEARTBEAT.outgoing)
  }

  _stopHeartbeat() {
    if (this._heartbeatTimer) {
      clearInterval(this._heartbeatTimer)
      this._heartbeatTimer = null
    }
  }

  /** 从 STOMP ERROR 帧中提取 message 头 */
  _parseErrorMessage(rawFrame) {
    const lines = rawFrame.trim().split('\n')
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i]
      if (line === '') break
      const colonIdx = line.indexOf(':')
      if (colonIdx > 0) {
        const key = line.substring(0, colonIdx).trim()
        const val = line.substring(colonIdx + 1).trim()
        if (key === 'message') return val
      }
    }
    // fallback: 取 body 部分（第一个空行之后的内容）
    const bodyIdx = lines.indexOf('')
    if (bodyIdx > 0 && bodyIdx < lines.length - 1) {
      return lines.slice(bodyIdx + 1).join('\n').trim() || null
    }
    return null
  }
}

/** 创建预配置的聊天 STOMP 连接 */
export async function createChatStomp(token) {
  const { WS_URL } = await import('@/utils/config')
  const client = new StompClient()
  await client.connect(`${WS_URL}/chat`, token)
  return client
}
