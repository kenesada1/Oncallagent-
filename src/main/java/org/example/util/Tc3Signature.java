package org.example.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * 腾讯云 TC3-HMAC-SHA256 签名工具类
 * 用于签署腾讯云 API 请求
 */
public class Tc3Signature {

    private static final String SERVICE = "cls";
    private static final String HOST = "cls.ap-guangzhou.tencentcloudapi.com";
    private static final String ACTION = "SearchLog";
    private static final String VERSION = "2020-10-16";
    private static final String CONTENT_TYPE = "application/json";

    /**
     * 生成 Authorization 签名头
     * @param secretId 秘钥 ID
     * @param secretKey 秘钥 Key
     * @param timestamp 时间戳（秒）
     * @param payload 请求体
     * @return Authorization 字符串
     */
    public static String sign(String secretId, String secretKey, long timestamp, String payload) {
        // 1. 拼接规范请求串
        String httpRequestMethod = "POST";
        String canonicalUri = "/";
        String canonicalQueryString = "";
        String canonicalHeaders = "content-type:application/json\nhost:" + HOST + "\n";
        String signedHeaders = "content-type;host";
        String hashedPayload = sha256Hex(payload);

        String canonicalRequest = httpRequestMethod + "\n" +
                canonicalUri + "\n" +
                canonicalQueryString + "\n" +
                canonicalHeaders + "\n" +
                signedHeaders + "\n" +
                hashedPayload;

        // 2. 拼接待签名字符串
        String date = formatTimestampDate(timestamp);
        String credentialScope = date + "/" + SERVICE + "/tc3_request";

        String stringToSign = "TC3-HMAC-SHA256\n" +
                timestamp + "\n" +
                credentialScope + "\n" +
                sha256Hex(canonicalRequest);

        // 3. 计算签名
        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = bytesToHex(hmacSha256(secretSigning, stringToSign));

        // 4. 拼接 Authorization
        return "TC3-HMAC-SHA256 " +
                "Credential=" + secretId + "/" + credentialScope + ", " +
                "SignedHeaders=" + signedHeaders + ", " +
                "Signature=" + signature;
    }

    /**
     * 生成完整的签名头 Map
     */
    public static java.util.Map<String, String> buildHeaders(String secretId, String secretKey, String payload) {
        long timestamp = System.currentTimeMillis() / 1000;
        String authorization = sign(secretId, secretKey, timestamp, payload);

        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Authorization", authorization);
        headers.put("Content-Type", CONTENT_TYPE);
        headers.put("Host", HOST);
        headers.put("X-TC-Action", ACTION);
        headers.put("X-TC-Version", VERSION);
        headers.put("X-TC-Timestamp", String.valueOf(timestamp));
        headers.put("X-TC-Region", "ap-guangzhou");

        return headers;
    }

    private static String sha256Hex(String data) {
        return bytesToHex(sha256(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] sha256(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (Exception e) {
            throw new RuntimeException("SHA256 error", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        return hmacSha256(key, data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
            mac.init(secretKeySpec);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 error", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String formatTimestampDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(timestamp * 1000));
    }
}