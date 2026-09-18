package com.yutong.common.encrypt;

import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 加密器快捷工具 — 业务方最常用入口
 * 设计来源: ADR 0004 P2-A common-encrypt
 *
 * 落点:21-安全合规详设「字段级加解密」
 *
 * 用法 (单例模式, 启动时 init 一次):
 *   @PostConstruct
 *   public void init() {
 *       byte[] key = Base64.getDecoder().decode(env.get("YUTONG_ENCRYPT_MASTER_KEY"));
 *       Encryptors.initDefault(new AesGcmEncryptor(key));
 *   }
 *
 *   // 业务代码:
 *   user.setIdCardNo(Encryptors.defaultEncryptor().encrypt(plain, "id_card"));
 *   String plain = Encryptors.defaultEncryptor().decrypt(user.getIdCardNo(), "id_card");
 *
 * 多算法场景:
 *   Encryptors.register("sm4", new Sm4Encryptor(...));
 *   Encryptors.get("sm4").encrypt(plain, "id_card");
 */
public final class Encryptors {

    private static final Map<String, Encryptor> ENCRYPTORS = new ConcurrentHashMap<>();
    private static volatile Encryptor DEFAULT;
    private static final Object LOCK = new Object();

    private Encryptors() {
    }

    /** 注册默认加密器 (启动时调用, 单例) */
    public static void initDefault(Encryptor encryptor) {
        synchronized (LOCK) {
            DEFAULT = encryptor;
        }
    }

    /** 注册命名加密器 (多算法场景) */
    public static void register(String name, Encryptor encryptor) {
        ENCRYPTORS.put(name, encryptor);
    }

    /** 取默认加密器 */
    public static Encryptor defaultEncryptor() {
        Encryptor d = DEFAULT;
        if (d == null) {
            throw new EncryptException(
                    "默认加密器未初始化, 请在启动时调用 Encryptors.initDefault(...), 或注入 MasterKeyProvider Bean");
        }
        return d;
    }

    /** 按名称取加密器 */
    public static Encryptor get(String name) {
        Encryptor e = ENCRYPTORS.get(name);
        if (e == null) {
            throw new EncryptException("加密器未注册: " + name);
        }
        return e;
    }

    /** 工具方法: Base64 编码 master key (dev 环境用) */
    public static String generateDevMasterKey() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
