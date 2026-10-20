package com.ikeu.common.utils;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ikeu.common.constant.MessageConstant;
import com.ikeu.common.exception.BusinessException;
import com.ikeu.common.properties.WeChatProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信小程序登录工具类：调用 code2Session 交换 openid
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatAuthUtil {

    private final WeChatProperties weChatProperties;

    /**
     * 用 wx.login 返回的 code 换取 openid / session_key / unionid
     * @return {openid, session_key, unionid}
     */
    public JSONObject code2Session(String code) {
        Map<String, String> params = new HashMap<>();
        params.put("appid", weChatProperties.getAppId());
        params.put("secret", weChatProperties.getAppSecret());
        params.put("js_code", code);
        params.put("grant_type", weChatProperties.getGrantType());

        try {
            String response = HttpClientUtil.doGet(weChatProperties.getLoginUrl(), params);
            JSONObject json = JSONUtil.parseObj(response);
            log.debug("微信 code2Session 成功, openid={}", json.getStr("openid"));

            if (json.containsKey("errcode") && json.getInt("errcode") != 0) {
                log.warn("微信登录失败: errcode={}, errmsg={}",
                        json.getInt("errcode"), json.getStr("errmsg"));
                throw new BusinessException(MessageConstant.WECHAT_LOGIN_FAILED + ": " + json.getStr("errmsg"));
            }

            return json;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信 code2Session 调用异常", e);
            throw new BusinessException(MessageConstant.WECHAT_SERVICE_UNAVAILABLE);
        }
    }
}
