package com.ikeu.common.utils;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.util.Map;

/**
 * 阿里云OSS工具类
 */
@Data
@AllArgsConstructor
@Slf4j
public class AliOssUtil {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".png", "image/png"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".pdf", "application/pdf"),
            Map.entry(".doc", "application/msword"),
            Map.entry(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry(".xls", "application/vnd.ms-excel"),
            Map.entry(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry(".ppt", "application/vnd.ms-powerpoint"),
            Map.entry(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry(".txt", "text/plain")
    );

    /**
     * 文件上传
     *
     * @param bytes
     * @param objectName
     * @return
     */
    public String upload(byte[] bytes, String objectName) {

        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            String suffix = "";
            int dotIndex = objectName.lastIndexOf('.');
            if (dotIndex > 0) {
                suffix = objectName.substring(dotIndex).toLowerCase();
            }
            metadata.setContentType(CONTENT_TYPES.getOrDefault(suffix, "application/octet-stream"));
            ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(bytes), metadata);
        } catch (OSSException oe) {
            log.error("OSS上传失败: ErrorCode={}, ErrorMessage={}, RequestId={}",
                    oe.getErrorCode(), oe.getErrorMessage(), oe.getRequestId());
            throw new RuntimeException("文件上传到OSS失败: " + oe.getErrorMessage(), oe);
        } catch (ClientException ce) {
            log.error("OSS客户端异常: {}", ce.getMessage());
            throw new RuntimeException("文件上传客户端异常: " + ce.getMessage(), ce);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }

        String filePath = "https://" + bucketName + "." + endpoint + "/" + objectName;
        log.info("文件上传到:{}", filePath);
        return filePath;
    }
}
