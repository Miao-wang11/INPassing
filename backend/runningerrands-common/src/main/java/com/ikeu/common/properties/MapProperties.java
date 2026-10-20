package com.ikeu.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 地图/LBS API 配置属性
 * 支持高德(amap)、百度(baidu)、腾讯(tencent)等地图服务商
 */
@Data
@Component
@ConfigurationProperties(prefix = "runningerrands.map")
public class MapProperties {

    /** 地图服务商：amap / baidu / tencent */
    private String provider;

    /** Web服务API Key */
    private String apiKey;

    /** 地理编码API地址 */
    private String geocodeUrl;

    /** 逆地理编码API地址 */
    private String reGeocodeUrl;

    /** 距离测量API地址 */
    private String distanceUrl;

    /** 默认搜索半径（公里） */
    private Double defaultRadiusKm;

    /** 坐标系：gcj02 / wgs84 / bd09 */
    private String coordinateSystem = "gcj02";
}
