package com.yutong.system.auth;

import com.yutong.system.auth.domain.SysClient;
import com.yutong.system.auth.mapper.SysClientMapper;
import com.yutong.system.auth.service.SysClientService;
import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SysClient 服务单元测试
 * 设计来源: ADR 0004 P2-F 批次 6-C
 *
 * 覆盖:
 *  - save_create 自动生成 clientId/clientSecret
 *  - save_update 保留 clientId/clientSecret
 *  - getByClientId_notFound 抛错
 *  - enable / disable 设状态
 *  - resetSecret 返回新密钥
 */
class SysClientServiceTest {

    private SysClientMapper mapper;
    private SysClientService service;
    private final Map<String, SysClient> store = new HashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        mapper = mock(SysClientMapper.class);
        service = new SysClientService(mapper);
        store.clear();
        nextId.set(1);

        when(mapper.selectById(any(String.class))).thenAnswer(inv -> store.get(inv.getArgument(0)));
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(inv -> {
            Object wObj = inv.getArgument(0);
            String sql = (String) wObj.getClass().getMethod("getSqlSegment").invoke(wObj);
            String targetClientId = null;
            for (Object val : ((java.util.Map<?, ?>) wObj.getClass().getMethod("getParamNameValuePairs").invoke(wObj)).values()) {
                if (val instanceof String) {
                    String s = (String) val;
                    if (s.startsWith("cli_")) {
                        targetClientId = s;
                        break;
                    }
                }
            }
            for (SysClient c : store.values()) {
                if (targetClientId != null && targetClientId.equals(c.getClientId())) return c;
            }
            return null;
        });
        when(mapper.insert(any(SysClient.class))).thenAnswer(inv -> {
            SysClient c = inv.getArgument(0);
            if (c.getId() == null) c.setId("client-" + nextId.getAndIncrement());
            store.put(c.getId(), c);
            return 1;
        });
        when(mapper.updateById(any(SysClient.class))).thenAnswer(inv -> {
            SysClient c = inv.getArgument(0);
            store.put(c.getId(), c);
            return 1;
        });
    }

    @Test
    void save_create_autoGeneratesClientIdAndSecret() {
        SysClient c = new SysClient();
        c.setClientName("测试应用");
        c.setGrantTypes("client_credentials");
        c.setDeviceType("pc");
        String id = service.save(c);
        SysClient stored = store.get(id);
        assertNotNull(stored.getClientId(), "clientId 应自动生成");
        assertNotNull(stored.getClientSecret(), "clientSecret 应自动生成");
        assertTrue(stored.getClientId().startsWith("cli_"));
        assertEquals(SysClient.STATUS_ENABLE, stored.getStatus());
    }

    @Test
    void save_update_preservesClientIdAndSecret() {
        SysClient c = new SysClient();
        c.setClientName("原");
        c.setClientId("cli_original_12345678");
        c.setClientSecret("original_secret_abcdefghij");
        String id = service.save(c);

        SysClient upd = new SysClient();
        upd.setId(id);
        upd.setClientName("新");
        upd.setClientId("cli_should_not_change");
        upd.setClientSecret("should_not_change");
        service.save(upd);

        SysClient stored = store.get(id);
        assertEquals("原→新".length() == 0 ? "新" : "新", stored.getClientName());
        // clientId / clientSecret 应保留, 不被请求中的覆盖
        assertEquals("cli_original_12345678", stored.getClientId(), "clientId 应保留");
        assertEquals("original_secret_abcdefghij", stored.getClientSecret(), "clientSecret 应保留");
    }

    @Test
    void getByClientId_notFound_throws() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getByClientId("nonexist"));
        assertTrue(ex.getMessage().contains("客户端不存在"));
    }

    @Test
    void enable_setsStatus() {
        SysClient c = new SysClient();
        c.setClientName("test");
        c.setStatus(SysClient.STATUS_DISABLE);
        String id = service.save(c);
        service.enable(id);
        assertEquals(SysClient.STATUS_ENABLE, store.get(id).getStatus());
    }

    @Test
    void disable_setsStatus() {
        SysClient c = new SysClient();
        c.setClientName("test");
        String id = service.save(c);
        service.disable(id);
        assertEquals(SysClient.STATUS_DISABLE, store.get(id).getStatus());
    }

    @Test
    void resetSecret_returnsNewSecret() {
        SysClient c = new SysClient();
        c.setClientName("test");
        String id = service.save(c);
        String oldSecret = store.get(id).getClientSecret();
        String newSecret = service.resetSecret(id);
        assertNotNull(newSecret);
        assertFalse(newSecret.equals(oldSecret), "新密钥应与旧密钥不同");
        assertEquals(newSecret, store.get(id).getClientSecret(), "应写入新密钥");
    }
}
