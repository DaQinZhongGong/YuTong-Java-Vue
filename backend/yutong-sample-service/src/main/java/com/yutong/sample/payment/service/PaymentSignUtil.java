package com.yutong.sample.payment.service;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 支付签名工具。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单 (GA2-41)。
 *
 * <p>HMAC-SHA256 签名算法 (复用 GA2-38 ExtSignatureService 思路):
 * <pre>
 * 签名串 = orderNo + "\n" + channel + "\n" + amount + "\n" + callbackTimestamp
 * signature = HMAC-SHA256(secretKey, 签名串)
 * </pre>
 *
 * <p>使用场景:
 * <ul>
 *   <li>Mock 第三方支付平台模拟回调时生成签名</li>
 *   <li>本服务接收回调时验签</li>
 * </ul>
 */
@Component
public class PaymentSignUtil {

    private static final Logger log = LoggerFactory.getLogger(PaymentSignUtil.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SHA256_ALGORITHM = "SHA-256";

    /**
     * 生成 HMAC-SHA256 签名。
     *
     * @param orderNo          支付单号
     * @param channel          支付渠道
     * @param amount           支付金额 (分)
     * @param callbackTimestamp 回调时间戳 (yyyyMMddHHmmss 格式)
     * @param secretKey        渠道密钥
     * @return 十六进制签名值
     */
    public String sign(String orderNo, String channel, Long amount, String callbackTimestamp, String secretKey) {
        try {
            String signString = buildSignString(orderNo, channel, amount, callbackTimestamp);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] signature = mac.doFinal(signString.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signature);
        } catch (Exception e) {
            log.error("sign failed: orderNo={} channel={}", orderNo, channel, e);
            throw new BusinessException(ErrorCode.PAY_CALLBACK_VERIFY_FAILED, "签名生成失败: " + e.getMessage());
        }
    }

    /**
     * 验证签名。返回 true 通过, false 失败。
     *
     * @param orderNo          支付单号
     * @param channel          支付渠道
     * @param amount           支付金额 (分)
     * @param callbackTimestamp 回调时间戳
     * @param signature        接收到的签名 (十六进制)
     * @param secretKey        渠道密钥
     * @return true 通过, false 失败
     */
    public boolean verify(String orderNo, String channel, Long amount, String callbackTimestamp,
                          String signature, String secretKey) {
        if (signature == null || signature.isBlank()) {
            log.warn("verify failed: signature is empty, orderNo={}", orderNo);
            return false;
        }
        String expected = sign(orderNo, channel, amount, callbackTimestamp, secretKey);
        if (!expected.equals(signature)) {
            log.warn("verify failed: signature mismatch orderNo={} expected={} actual={}", orderNo, expected, signature);
            return false;
        }
        return true;
    }

    /**
     * 构造签名串: orderNo + "\n" + channel + "\n" + amount + "\n" + callbackTimestamp。
     */
    private String buildSignString(String orderNo, String channel, Long amount, String callbackTimestamp) {
        return String.join("\n",
                orderNo == null ? "" : orderNo,
                channel == null ? "" : channel,
                amount == null ? "" : amount.toString(),
                callbackTimestamp == null ? "" : callbackTimestamp);
    }

    /**
     * 计算字符串 SHA-256 十六进制哈希 (审计日志摘要用)。
     */
    public String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance(SHA256_ALGORITHM);
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
