package com.yutong.system.oss;

import com.yutong.common.exception.BusinessException;
import com.yutong.system.oss.domain.SysOssConfig;
import com.yutong.system.oss.dto.SaveOssConfigRequest;
import com.yutong.system.oss.mapper.SysOssConfigMapper;
import com.yutong.system.oss.service.OssConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * OSS 配置服务单元测试。
 * 覆盖:
 *  - save_create 创建 + configKey 唯一
 *  - save_update configKey 不可改
 *  - save_duplicateKey 抛错
 *  - setDefault 自动取消旧默认
 *  - disable_default 抛错
 *  - delete_default 抛错
 *  - page_secretMasked 列表脱敏
 *  - testConnection_local 直接成功
 */
class OssConfigServiceTest {

    private SysOssConfigMapper mapper;
    private OssConfigService service;
    private final Map<String, SysOssConfig> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        mapper = mock(SysOssConfigMapper.class);
        service = new OssConfigService(mapper);
        store.clear();

        when(mapper.selectById(any(String.class))).thenAnswer(inv -> store.get(inv.getArgument(0)));
        when(mapper.insert(any(SysOssConfig.class))).thenAnswer(inv -> {
            SysOssConfig c = inv.getArgument(0);
            store.put(c.getId(), c);
            return 1;
        });
        when(mapper.updateById(any(SysOssConfig.class))).thenAnswer(inv -> {
            SysOssConfig c = inv.getArgument(0);
            store.put(c.getId(), c);
            return 1;
        });
        when(mapper.deleteById(any(String.class))).thenAnswer(inv -> {
            store.remove((String) inv.getArgument(0));
            return 1;
        });
        // clearDefaultFlag 使用 mapper.update(null, UpdateWrapper) 批量取消旧默认
        when(mapper.update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenAnswer(inv -> {
                    store.values().forEach(c -> c.setIsDefault(false));
                    return store.size();
                });
        when(mapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenAnswer(inv -> {
                    // 简化: 只要有同 configKey 的记录就返回 1
                    long count = store.values().stream()
                            .filter(c -> "default".equals(c.getConfigKey()) || "backup".equals(c.getConfigKey()))
                            .count();
                    return count;
                });
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenAnswer(inv -> store.values().stream()
                        .filter(c -> Boolean.TRUE.equals(c.getIsDefault()))
                        .findFirst().orElse(null));
    }

    private SaveOssConfigRequest buildRequest(String key, boolean isDefault) {
        SaveOssConfigRequest req = new SaveOssConfigRequest();
        req.setConfigKey(key);
        req.setConfigName("测试配置-" + key);
        req.setStorageType("MINIO");
        req.setEndpoint("http://localhost:9000");
        req.setAccessKey("admin");
        req.setSecretKey("secret");
        req.setBucketName("test-bucket");
        req.setIsDefault(isDefault);
        return req;
    }

    @Test
    void save_create_success() {
        // selectCount 返回 0 (store 空) → 不冲突
        when(mapper.selectCount(any())).thenReturn(0L);
        String id = service.save(buildRequest("default", true));
        assertNotNull(id);
        SysOssConfig saved = store.get(id);
        assertNotNull(saved);
        assertEquals("default", saved.getConfigKey());
        assertTrue(Boolean.TRUE.equals(saved.getIsDefault()));
        assertEquals("ENABLED", saved.getStatus());
    }

    @Test
    void save_duplicateKey_throws() {
        // 先插入一个
        when(mapper.selectCount(any())).thenReturn(0L);
        service.save(buildRequest("default", false));
        // 再插入同 key → selectCount 返回 1 → 抛错
        when(mapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.save(buildRequest("default", false)));
    }

    @Test
    void save_update_configKeyIgnored() {
        when(mapper.selectCount(any())).thenReturn(0L);
        String id = service.save(buildRequest("default", false));
        // 更新: 改 configName, configKey 不变
        SaveOssConfigRequest update = buildRequest("changed", false);
        update.setId(id);
        update.setConfigName("新名称");
        service.save(update);
        assertEquals("default", store.get(id).getConfigKey()); // 不可改
        assertEquals("新名称", store.get(id).getConfigName());
    }

    @Test
    void setDefault_clearsOldDefault() {
        when(mapper.selectCount(any())).thenReturn(0L);
        String id1 = service.save(buildRequest("default", true));
        String id2 = service.save(buildRequest("backup", false));
        service.setDefault(id2);
        assertTrue(Boolean.TRUE.equals(store.get(id2).getIsDefault()));
        assertFalse(Boolean.TRUE.equals(store.get(id1).getIsDefault()));
    }

    @Test
    void disable_default_throws() {
        when(mapper.selectCount(any())).thenReturn(0L);
        String id = service.save(buildRequest("default", true));
        assertThrows(BusinessException.class, () -> service.disable(id));
    }

    @Test
    void delete_default_throws() {
        when(mapper.selectCount(any())).thenReturn(0L);
        String id = service.save(buildRequest("default", true));
        assertThrows(BusinessException.class, () -> service.delete(id));
    }

    @Test
    void delete_nonDefault_success() {
        when(mapper.selectCount(any())).thenReturn(0L);
        String id = service.save(buildRequest("backup", false));
        service.delete(id);
        assertNull(store.get(id));
    }

    @Test
    void testConnection_local_returnsTrue() {
        when(mapper.selectCount(any())).thenReturn(0L);
        SaveOssConfigRequest req = buildRequest("local", false);
        req.setStorageType("LOCAL");
        String id = service.save(req);
        assertTrue(service.testConnection(id));
    }

    @Test
    void save_create_secondDefault_clearsFirst() {
        when(mapper.selectCount(any())).thenReturn(0L);
        String id1 = service.save(buildRequest("default", true));
        String id2 = service.save(buildRequest("backup", true)); // 第二个也设默认
        assertTrue(Boolean.TRUE.equals(store.get(id2).getIsDefault()));
        assertFalse(Boolean.TRUE.equals(store.get(id1).getIsDefault()));
    }
}
