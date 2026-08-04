package com.yutong.auth.emergency;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yutong.auth.domain.AuthEmergencyAdmin;
import com.yutong.auth.mapper.AuthEmergencyAdminMapper;
import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmergencyAdminService 凭据安全单元测试。
 * 设计来源: 32-企业级权限与租户接入方案、21-安全与质量设计 A02 加密失败。
 *
 * <p>覆盖场景:
 * <ol>
 *   <li>create: 密钥以 BCrypt 摘要落库，不存明文</li>
 *   <li>create: 空密钥 / 弱口令拒绝</li>
 *   <li>use: 正确密钥校验通过并累加使用次数</li>
 *   <li>use: 错误密钥拒绝</li>
 *   <li>use: 历史明文凭据一律拒绝（不做明文回退比较）</li>
 *   <li>use: 空密钥 / 未启用 / 超次数拒绝</li>
 *   <li>resetSecret: 重置为 BCrypt 并复位 DISABLED</li>
 * </ol>
 */
@DisplayName("EmergencyAdminService: 应急管理员 BCrypt 凭据")
class EmergencyAdminServiceTest {

    private static final String TENANT = "default";
    private static final String ADMIN_CODE = "EMERGENCY_01";
    private static final String RAW_SECRET = "Str0ng-Emergency-Secret";

    private AuthEmergencyAdminMapper adminMapper;
    private EmergencyAdminService service;

    @BeforeEach
    void setUp() {
        adminMapper = mock(AuthEmergencyAdminMapper.class);
        service = new EmergencyAdminService(adminMapper);
    }

    private AuthEmergencyAdmin enabledAdmin(String secretHash) {
        AuthEmergencyAdmin admin = new AuthEmergencyAdmin();
        admin.setId("01HZZTESTEMERGENCYADMIN000000001");
        admin.setTenantId(TENANT);
        admin.setAdminCode(ADMIN_CODE);
        admin.setAdminName("应急管理员01");
        admin.setSecretHash(secretHash);
        admin.setStatus(AuthEmergencyAdmin.STATUS_ENABLED);
        admin.setUsageCount(0);
        admin.setMaxUsageCount(5);
        return admin;
    }

    @SuppressWarnings("unchecked")
    private void stubLoad(AuthEmergencyAdmin admin) {
        when(adminMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(admin);
    }

    @Nested
    @DisplayName("create: 密钥必须 BCrypt 存储")
    class Create {

        @Test
        @DisplayName("密钥以 BCrypt 摘要落库，不等于明文且可被 matches 校验")
        void shouldStoreBcryptHash() {
            when(adminMapper.insert(any(AuthEmergencyAdmin.class))).thenReturn(1);

            service.create(TENANT, ADMIN_CODE, "应急管理员01", RAW_SECRET);

            ArgumentCaptor<AuthEmergencyAdmin> captor = ArgumentCaptor.forClass(AuthEmergencyAdmin.class);
            verify(adminMapper).insert(captor.capture());
            String stored = captor.getValue().getSecretHash();

            assertNotEquals(RAW_SECRET, stored, "落库密钥不得为明文");
            assertTrue(stored.startsWith("$2"), "落库密钥必须是 BCrypt 摘要，实际: " + stored);
            assertTrue(new BCryptPasswordEncoder().matches(RAW_SECRET, stored), "BCrypt 摘要应可校验原始密钥");
            assertEquals(AuthEmergencyAdmin.STATUS_DISABLED, captor.getValue().getStatus(), "新建应默认禁用");
        }

        @Test
        @DisplayName("空密钥拒绝创建")
        void shouldRejectBlankSecret() {
            assertThrows(BusinessException.class, () -> service.create(TENANT, ADMIN_CODE, "n", "  "));
            verify(adminMapper, never()).insert(any(AuthEmergencyAdmin.class));
        }

        @Test
        @DisplayName("弱口令(长度不足)拒绝创建")
        void shouldRejectWeakSecret() {
            assertThrows(BusinessException.class, () -> service.create(TENANT, ADMIN_CODE, "n", "short1"));
            verify(adminMapper, never()).insert(any(AuthEmergencyAdmin.class));
        }
    }

    @Nested
    @DisplayName("use: 只走 BCrypt 校验")
    class Use {

        @Test
        @DisplayName("正确密钥校验通过，状态转 IN_USE 且使用次数 +1")
        void shouldAcceptCorrectSecret() {
            AuthEmergencyAdmin admin = enabledAdmin(new BCryptPasswordEncoder(12).encode(RAW_SECRET));
            stubLoad(admin);
            when(adminMapper.updateById(any(AuthEmergencyAdmin.class))).thenReturn(1);

            assertTrue(service.use(TENANT, ADMIN_CODE, RAW_SECRET));
            assertEquals(AuthEmergencyAdmin.STATUS_IN_USE, admin.getStatus());
            assertEquals(1, admin.getUsageCount());
        }

        @Test
        @DisplayName("错误密钥拒绝，不更新记录")
        void shouldRejectWrongSecret() {
            stubLoad(enabledAdmin(new BCryptPasswordEncoder(12).encode(RAW_SECRET)));

            assertFalse(service.use(TENANT, ADMIN_CODE, "wrong-secret-value"));
            verify(adminMapper, never()).updateById(any(AuthEmergencyAdmin.class));
        }

        @Test
        @DisplayName("历史明文凭据一律拒绝（不做明文回退比较）")
        void shouldRejectLegacyPlaintextHash() {
            stubLoad(enabledAdmin(RAW_SECRET));

            assertFalse(service.use(TENANT, ADMIN_CODE, RAW_SECRET), "明文 secret_hash 不得被接受");
            verify(adminMapper, never()).updateById(any(AuthEmergencyAdmin.class));
        }

        @Test
        @DisplayName("空密钥直接拒绝，不查库")
        void shouldRejectBlankSecret() {
            assertFalse(service.use(TENANT, ADMIN_CODE, null));
            verify(adminMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("未启用状态拒绝使用")
        void shouldRejectDisabledAdmin() {
            AuthEmergencyAdmin admin = enabledAdmin(new BCryptPasswordEncoder(12).encode(RAW_SECRET));
            admin.setStatus(AuthEmergencyAdmin.STATUS_DISABLED);
            stubLoad(admin);

            assertFalse(service.use(TENANT, ADMIN_CODE, RAW_SECRET));
        }

        @Test
        @DisplayName("使用次数达上限拒绝使用")
        void shouldRejectWhenUsageExhausted() {
            AuthEmergencyAdmin admin = enabledAdmin(new BCryptPasswordEncoder(12).encode(RAW_SECRET));
            admin.setUsageCount(5);
            admin.setMaxUsageCount(5);
            stubLoad(admin);

            assertFalse(service.use(TENANT, ADMIN_CODE, RAW_SECRET));
        }
    }

    @Nested
    @DisplayName("resetSecret: 明文凭据迁移通道")
    class ResetSecret {

        @Test
        @DisplayName("重置后为 BCrypt 摘要，状态复位 DISABLED，使用次数清零")
        void shouldRehashAndDisable() {
            AuthEmergencyAdmin admin = enabledAdmin(RAW_SECRET);
            admin.setUsageCount(3);
            stubLoad(admin);
            when(adminMapper.updateById(any(AuthEmergencyAdmin.class))).thenReturn(1);

            assertTrue(service.resetSecret(TENANT, ADMIN_CODE, "New-Str0ng-Secret", "ops-admin"));
            assertTrue(admin.getSecretHash().startsWith("$2"));
            assertTrue(new BCryptPasswordEncoder().matches("New-Str0ng-Secret", admin.getSecretHash()));
            assertEquals(AuthEmergencyAdmin.STATUS_DISABLED, admin.getStatus());
            assertEquals(0, admin.getUsageCount());
        }

        @Test
        @DisplayName("弱口令拒绝重置")
        void shouldRejectWeakSecret() {
            assertThrows(BusinessException.class,
                    () -> service.resetSecret(TENANT, ADMIN_CODE, "weak", "ops-admin"));
            verify(adminMapper, never()).updateById(any(AuthEmergencyAdmin.class));
        }
    }
}
