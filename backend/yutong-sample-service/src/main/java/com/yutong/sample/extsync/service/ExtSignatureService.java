package com.yutong.sample.extsync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.sample.extsync.domain.ExtSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 外部接口签名鉴权服务。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * <p>HMAC-SHA256 签名算法:
 * <pre>
 * 签名串 = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body_hash
 * signature = HMAC-SHA256(secretKey, 签名串)
 * </pre>
 *
 * <p>请求头:
 * <ul>
 *   <li>X-Ext-Access-Key: accessKey (明文, 用于服务端查到对应 secretKey)</li>
 *   <li>X-Ext-Timestamp: 毫秒时间戳 (防重放, 服务端校验 5 分钟偏移)</li>
 *   <li>X-Ext-Nonce: 32 字符随机串 (防重放)</li>
 *   <li>X-Ext-Signature: 十六进制签名值</li>
 * </ul>
 */
@Service
public class ExtSignatureService {

    private static final Logger log = LoggerFactory.getLogger(ExtSignatureService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SHA256_ALGORITHM = "SHA-256";

    private final ObjectMapper objectMapper;

    public ExtSignatureService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 生成 HMAC-SHA256 签名。
     */
    public String sign(String method, String path, String timestamp, String nonce, String body, ExtSystem system) {
        try {
            String secretKey = extractSecretKey(system);
            String bodyHash = sha256Hex(body == null ? "" : body);
            String signString = String.join("\n", method, path, timestamp, nonce, bodyHash);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] signature = mac.doFinal(signString.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signature);
        } catch (Exception e) {
            log.error("sign failed: system={}", system.getSystemCode(), e);
            throw new BusinessException(ErrorCode.EXT_SYNC_EXECUTION_FAILED, "签名生成失败: " + e.getMessage());
        }
    }

    /**
     * 验证签名 (Mock 外部系统端使用)。返回 true 通过, false 失败。
     */
    public boolean verify(String method, String path, String accessKey, String timestamp, String nonce,
                          String body, String signature, String expectedAccessKey, String expectedSecretKey) {
        if (!expectedAccessKey.equals(accessKey)) {
            log.warn("verify failed: accessKey mismatch expected={} actual={}", expectedAccessKey, accessKey);
            return false;
        }
        String expected = sign(method, path, timestamp, nonce, body,
                buildTempSystem(expectedAccessKey, expectedSecretKey));
        if (!expected.equals(signature)) {
            log.warn("verify failed: signature mismatch expected={} actual={}", expected, signature);
            return false;
        }
        return true;
    }

    private ExtSystem buildTempSystem(String accessKey, String secretKey) {
        ExtSystem sys = new ExtSystem();
        sys.setAuthType(ExtSystem.AUTH_HMAC_SHA256);
        sys.setCredentials("{\"accessKey\":\"" + accessKey + "\",\"secretKey\":\"" + secretKey + "\"}");
        return sys;
    }

    private String extractSecretKey(ExtSystem system) {
        try {
            JsonNode node = objectMapper.readTree(system.getCredentials());
            JsonNode secret = node.get("secretKey");
            if (secret == null || secret.isNull()) {
                throw new BusinessException(ErrorCode.EXT_REQUEST_INVALID,
                        "外部系统 credentials 缺少 secretKey: " + system.getSystemCode());
            }
            return secret.asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXT_REQUEST_INVALID,
                    "外部系统 credentials 解析失败: " + e.getMessage());
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance(SHA256_ALGORITHM);
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
