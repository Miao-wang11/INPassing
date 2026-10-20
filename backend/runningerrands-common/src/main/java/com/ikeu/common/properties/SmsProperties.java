package com.ikeu.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "runningerrands.sms")
public class SmsProperties {

    private String accessKeyId;
    private String accessKeySecret;
    private String signName;        // 短信签名
    private String templateCode;    // 验证码模板编号
    private String regionId;
    private String endpoint;

}