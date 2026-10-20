package com.ikeu.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "runningerrands.wechat")
public class WeChatProperties {

    /** 小程序 AppID */
    private String appId;

    /** 小程序 AppSecret */
    private String appSecret;

    /** 登录接口地址 */
    private String loginUrl = "https://api.weixin.qq.com/sns/jscode2session";

    /** 授权类型 */
    private String grantType = "authorization_code";

}
