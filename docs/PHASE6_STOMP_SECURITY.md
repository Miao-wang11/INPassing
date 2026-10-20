# WebSocket 聊天身份与目标权限

底座的 STOMP 握手 JWT 已验证用户 ID，但消息通道需要显式绑定 Principal，并拒绝任意订阅目标，否则聊天消息可能无法正确通过 convertAndSendToUser 路由，甚至暴露他人的广播订阅。

本修复在 CONNECT 时从服务器 JWT 握手 session attributes 取出 userId 作为 STOMP Principal；SEND 只允许 /app/chat.send；SUBSCRIBE 只允许 /user/queue/chat、/user/notification 和 /user/queue/notification。用户不可指定 /user/{otherId}/... 或 /topic 等公共订阅目标。无 JWT 会话拒绝 STOMP 命令。

聊天服务对发送消息、历史、标为已读分别使用已审核校区做权限校验，同校区已认证用户才能交互；画像/封禁状态要有效；发送者无法自发消息，也不能发送空消息、超长文字或不合规消息类型。聊天联系人列表过滤失效/跨校区身份。

注意：本修复不保证微信/Android 真机的断网自动重连可靠性，也未测试万人在线的消息吞吐、WebSocket 断线期间离线推送。离线历史来自 MySQL chat_message，前端重新连接时可通过 REST 查询；后续还要验证完整客户端。
