package com.ikeu.common.utils;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.teaopenapi.models.Config;
import com.ikeu.common.properties.SmsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 阿里云短信验证服务工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsUtil {

    private final SmsProperties smsProperties;

    public void sendVerifyCode(String phone, String code) throws Exception {
        // 1. 构建阿里云配置
        Config config = new Config()
                .setAccessKeyId(smsProperties.getAccessKeyId())
                .setAccessKeySecret(smsProperties.getAccessKeySecret())
                .setEndpoint(smsProperties.getEndpoint());   // dypnsapi.aliyuncs.com

        // region 可以随便设置，实际由 endpoint 决定
        config.setRegionId(smsProperties.getRegionId() != null ? smsProperties.getRegionId() : "cn-chengdu");

        // 2. 创建同步客户端
        Client client = new Client(config);

        // 4. 构建请求
        SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest();
        request.setPhoneNumber(phone);
        request.setSignName(smsProperties.getSignName());
        request.setTemplateCode(smsProperties.getTemplateCode());
        // 模板参数 JSON 字符串，变量名必须与模板中一致
        String templateParam = String.format("{\"code\":\"%s\",\"min\":\"5\"}", code);
        request.setTemplateParam(templateParam);
        request.setCodeLength(6L);      // 验证码长度
        request.setValidTime(5L);       // 有效期（分钟）
        request.setReturnVerifyCode(false); // 不返回验证码，我们自行保存

        // 5. 发送并处理结果
        SendSmsVerifyCodeResponse response = client.sendSmsVerifyCode(request);
        String message = response.getBody().getMessage();
        if ("OK".equalsIgnoreCase(message)) {
            log.info("短信发送成功，手机号：{}，验证码：{}", phone, code);
        } else {
            log.error("短信发送失败，手机号：{}，错误：{}", phone, message);
            throw new RuntimeException("短信发送失败: " + message);
        }
    }
}