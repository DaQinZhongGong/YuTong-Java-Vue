package com.yutong.lowcode.meta.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.response.PageResult;
import com.yutong.lowcode.meta.domain.LcComponentRegistry;
import com.yutong.lowcode.meta.dto.ComponentRegistryPageQuery;
import com.yutong.lowcode.meta.dto.SaveComponentRegistryRequest;
import com.yutong.lowcode.meta.mapper.LcComponentRegistryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 组件协议元数据应用服务单元测试。设计来源: 36-低代码高级能力设计 组件协议（GA2-L191 子任务 B）
 * 覆盖: create/page/get/getByCode/listByCategory/listPublished/update/delete/publish/disable
 * + code 重复 / 不存在 / 版本不匹配 / 状态流转非法。
 *
 * 注: MyBatis-Plus 3.5.16 的 BaseMapper 同时存在 insert(T) 与 insert(Collection<T>) 重载，
 * 匹配器在 insert/updateById 上需使用 Mockito.<T>any() 显式指定泛型类型避免歧义；
 * selectCount 返回 Long；selectPage/selectList/selectOne 仅接受单一签名，直接 any() 即可。
 */
@DisplayName("组件协议元数据应用服务")
@ExtendWith(MockitoExtension.class)
class ComponentRegistryApplicationServiceTest {

    @Mock
    private LcComponentRegistryMapper registryMapper;

    @InjectMocks
    private ComponentRegistryApplicationService applicationService;

    @BeforeEach
    void setUpContext() {
        CurrentUserContext.set("01TESTUSER0000000000000000001", "default", "测试员");
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    // ==================== create ====================

    @Nested
    @DisplayName("create: 新建组件协议")
    class Create {

        @Test
        @DisplayName("新建成功: 默认状态 DRAFT，默认平台 BOTH，默认版本 1.0.0")
        void createSuccess() {
            SaveComponentRegistryRequest req = buildSaveRequest("TextInput", "文本输入", "INPUT", "单行文本");
            // 置空可默认字段，验证默认值填充逻辑
            req.setPlatform(null);
            req.setStatus(null);
            req.setCompatibilityGrade(null);
            req.setComponentVersion(null);
            req.setSortNo(null);
            req.setDeprecated(null);
            req.setPermissionSupport(null);
            req.setValidationSupport(null);

            when(registryMapper.selectCount(any())).thenReturn(0L);
            when(registryMapper.insert(org.mockito.Mockito.<LcComponentRegistry>any())).thenReturn(1);

            LcComponentRegistry result = applicationService.create(req);

            assertNotNull(result);
            assertEquals("TextInput", result.getComponentCode());
            assertEquals(LcComponentRegistry.STATUS_DRAFT, result.getStatus());
            assertEquals(LcComponentRegistry.PLATFORM_BOTH, result.getPlatform());
            assertEquals(LcComponentRegistry.GRADE_STABLE, result.getCompatibilityGrade());
            assertEquals("1.0.0", result.getComponentVersion());
            assertEquals(0, result.getSortNo());
            assertFalse(result.getDeprecated());
            assertFalse(result.getPermissionSupport());
            assertFalse(result.getValidationSupport());
            verify(registryMapper).insert(org.mockito.Mockito.<LcComponentRegistry>any());
        }

        @Test
        @DisplayName("新建失败: component_code 重复抛 BusinessException(LC_ENTITY_CODE_DUPLICATE)")
        void createCodeDuplicate() {
            SaveComponentRegistryRequest req = buildSaveRequest("TextInput", "文本输入", "INPUT", "单行文本");

            when(registryMapper.selectCount(any())).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class, () -> applicationService.create(req));
            assertEquals(com.yutong.common.errorcode.ErrorCode.LC_ENTITY_CODE_DUPLICATE, ex.errorCode());
            verify(registryMapper, never()).insert(org.mockito.Mockito.<LcComponentRegistry>any());
        }
    }

    // ==================== page ====================

    @Nested
    @DisplayName("page: 分页查询")
    class PageQuery {

        @Test
        @DisplayName("分页查询成功: 按 category 过滤")
        void pageWithCategory() {
            LcComponentRegistry entity = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            Page<LcComponentRegistry> page = new Page<>(1, 20);
            page.setRecords(List.of(entity));
            page.setTotal(1L);
            when(registryMapper.selectPage(any(), any())).thenReturn(page);

            ComponentRegistryPageQuery query = new ComponentRegistryPageQuery();
            query.setCategory("INPUT");

            PageResult<LcComponentRegistry> result = applicationService.page(query);

            assertEquals(1, result.records().size());
            assertEquals(1L, result.total());
            assertEquals(1, result.page());
            assertEquals(20, result.size());
        }

        @Test
        @DisplayName("分页查询: 空结果")
        void pageEmpty() {
            Page<LcComponentRegistry> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0L);
            when(registryMapper.selectPage(any(), any())).thenReturn(page);

            ComponentRegistryPageQuery query = new ComponentRegistryPageQuery();
            PageResult<LcComponentRegistry> result = applicationService.page(query);

            assertTrue(result.records().isEmpty());
            assertEquals(0L, result.total());
        }
    }

    // ==================== get / getByCode ====================

    @Nested
    @DisplayName("get / getByCode: 详情查询")
    class Get {

        @Test
        @DisplayName("get: 详情查询成功")
        void getSuccess() {
            LcComponentRegistry entity = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            when(registryMapper.selectById(anyString())).thenReturn(entity);

            LcComponentRegistry result = applicationService.get("01LCREG0000000000000000001");

            assertEquals("TextInput", result.getComponentCode());
        }

        @Test
        @DisplayName("get: 不存在抛 ResourceNotFoundException")
        void getNotFound() {
            when(registryMapper.selectById(anyString())).thenReturn(null);
            assertThrows(ResourceNotFoundException.class,
                    () -> applicationService.get("nonexistent"));
        }

        @Test
        @DisplayName("getByCode: 按 code 查询成功")
        void getByCodeSuccess() {
            LcComponentRegistry entity = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            when(registryMapper.selectOne(any())).thenReturn(entity);

            LcComponentRegistry result = applicationService.getByCode("TextInput");

            assertNotNull(result);
            assertEquals("TextInput", result.getComponentCode());
        }

        @Test
        @DisplayName("getByCode: 不存在返回 null")
        void getByCodeNotFound() {
            when(registryMapper.selectOne(any())).thenReturn(null);
            assertNull(applicationService.getByCode("NotExists"));
        }
    }

    // ==================== listByCategory / listPublished ====================

    @Nested
    @DisplayName("listByCategory / listPublished: 列表查询")
    class ListQuery {

        @Test
        @DisplayName("listByCategory: 按分类返回列表")
        void listByCategory() {
            LcComponentRegistry e1 = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            LcComponentRegistry e2 = buildEntity("01LCREG0000000000000000002", "NumberInput", "数字输入", "INPUT");
            when(registryMapper.selectList(any())).thenReturn(List.of(e1, e2));

            List<LcComponentRegistry> result = applicationService.listByCategory("INPUT");

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("listPublished: 已发布列表返回非废弃 PUBLISHED 组件")
        void listPublished() {
            LcComponentRegistry entity = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            entity.setStatus(LcComponentRegistry.STATUS_PUBLISHED);
            when(registryMapper.selectList(any())).thenReturn(List.of(entity));

            List<LcComponentRegistry> result = applicationService.listPublished();

            assertEquals(1, result.size());
            assertEquals(LcComponentRegistry.STATUS_PUBLISHED, result.get(0).getStatus());
        }
    }

    // ==================== update ====================

    @Nested
    @DisplayName("update: 更新组件协议")
    class Update {

        @Test
        @DisplayName("更新成功: 名称变更，状态保持不变")
        void updateSuccess() {
            LcComponentRegistry existing = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            existing.setStatus(LcComponentRegistry.STATUS_DRAFT);
            existing.setVersion(0);

            SaveComponentRegistryRequest req = buildSaveRequest("TextInput", "文本输入-改", "INPUT", "单行文本");
            req.setVersion(0);

            when(registryMapper.selectById(anyString())).thenReturn(existing);
            when(registryMapper.selectCount(any())).thenReturn(0L);
            when(registryMapper.updateById(org.mockito.Mockito.<LcComponentRegistry>any())).thenReturn(1);

            LcComponentRegistry result = applicationService.update("01LCREG0000000000000000001", req);

            assertEquals("文本输入-改", result.getComponentName());
            // status 不通过 update 修改
            assertEquals(LcComponentRegistry.STATUS_DRAFT, result.getStatus());
        }

        @Test
        @DisplayName("更新失败: 不存在抛 ResourceNotFoundException")
        void updateNotFound() {
            SaveComponentRegistryRequest req = buildSaveRequest("TextInput", "文本输入", "INPUT", "单行文本");
            req.setVersion(0);
            when(registryMapper.selectById(anyString())).thenReturn(null);
            assertThrows(ResourceNotFoundException.class,
                    () -> applicationService.update("nonexistent", req));
        }

        @Test
        @DisplayName("更新失败: 版本号不匹配抛 BusinessConflictException")
        void updateVersionMismatch() {
            LcComponentRegistry existing = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            existing.setVersion(5);

            SaveComponentRegistryRequest req = buildSaveRequest("TextInput", "文本输入", "INPUT", "单行文本");
            req.setVersion(1); // 不匹配

            when(registryMapper.selectById(anyString())).thenReturn(existing);
            assertThrows(BusinessConflictException.class,
                    () -> applicationService.update("01LCREG0000000000000000001", req));
        }

        @Test
        @DisplayName("更新失败: code 重复抛 BusinessException(LC_ENTITY_CODE_DUPLICATE)")
        void updateCodeDuplicate() {
            LcComponentRegistry existing = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            existing.setVersion(0);

            SaveComponentRegistryRequest req = buildSaveRequest("NumberInput", "数字输入", "INPUT", "数字");
            req.setVersion(0);

            when(registryMapper.selectById(anyString())).thenReturn(existing);
            when(registryMapper.selectCount(any())).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> applicationService.update("01LCREG0000000000000000001", req));
            assertEquals(com.yutong.common.errorcode.ErrorCode.LC_ENTITY_CODE_DUPLICATE, ex.errorCode());
            verify(registryMapper, never()).updateById(org.mockito.Mockito.<LcComponentRegistry>any());
        }

        @Test
        @DisplayName("更新失败: updateById 返回 0 抛乐观锁异常")
        void updateOptimisticLockFailure() {
            LcComponentRegistry existing = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            existing.setVersion(0);

            SaveComponentRegistryRequest req = buildSaveRequest("TextInput", "文本输入-改", "INPUT", "单行文本");
            req.setVersion(0);

            when(registryMapper.selectById(anyString())).thenReturn(existing);
            when(registryMapper.selectCount(any())).thenReturn(0L);
            when(registryMapper.updateById(org.mockito.Mockito.<LcComponentRegistry>any())).thenReturn(0);

            assertThrows(BusinessConflictException.class,
                    () -> applicationService.update("01LCREG0000000000000000001", req));
        }
    }

    // ==================== delete ====================

    @Nested
    @DisplayName("delete: 逻辑删除")
    class Delete {

        @Test
        @DisplayName("删除成功")
        void deleteSuccess() {
            LcComponentRegistry existing = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            when(registryMapper.selectById(anyString())).thenReturn(existing);
            when(registryMapper.deleteById(anyString())).thenReturn(1);

            applicationService.delete("01LCREG0000000000000000001");

            verify(registryMapper).deleteById("01LCREG0000000000000000001");
        }

        @Test
        @DisplayName("删除失败: 不存在抛 ResourceNotFoundException")
        void deleteNotFound() {
            when(registryMapper.selectById(anyString())).thenReturn(null);
            assertThrows(ResourceNotFoundException.class,
                    () -> applicationService.delete("nonexistent"));
        }
    }

    // ==================== publish / disable ====================

    @Nested
    @DisplayName("publish / disable: 状态流转")
    class Transitions {

        @Test
        @DisplayName("publish: DRAFT → PUBLISHED 成功")
        void publishSuccess() {
            LcComponentRegistry existing = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            existing.setStatus(LcComponentRegistry.STATUS_DRAFT);
            when(registryMapper.selectById(anyString())).thenReturn(existing);
            when(registryMapper.updateById(org.mockito.Mockito.<LcComponentRegistry>any())).thenReturn(1);

            applicationService.publish("01LCREG0000000000000000001");

            assertEquals(LcComponentRegistry.STATUS_PUBLISHED, existing.getStatus());
        }

        @Test
        @DisplayName("publish: 已 PUBLISHED 幂等返回，不调用 updateById")
        void publishIdempotent() {
            LcComponentRegistry existing = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            existing.setStatus(LcComponentRegistry.STATUS_PUBLISHED);
            when(registryMapper.selectById(anyString())).thenReturn(existing);

            applicationService.publish("01LCREG0000000000000000001");

            verify(registryMapper, never()).updateById(org.mockito.Mockito.<LcComponentRegistry>any());
        }

        @Test
        @DisplayName("publish: DISABLED 状态不允许发布")
        void publishWrongState() {
            LcComponentRegistry existing = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            existing.setStatus(LcComponentRegistry.STATUS_DISABLED);
            when(registryMapper.selectById(anyString())).thenReturn(existing);

            assertThrows(BusinessConflictException.class,
                    () -> applicationService.publish("01LCREG0000000000000000001"));
        }

        @Test
        @DisplayName("disable: PUBLISHED → DISABLED 成功")
        void disableSuccess() {
            LcComponentRegistry existing = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            existing.setStatus(LcComponentRegistry.STATUS_PUBLISHED);
            when(registryMapper.selectById(anyString())).thenReturn(existing);
            when(registryMapper.updateById(org.mockito.Mockito.<LcComponentRegistry>any())).thenReturn(1);

            applicationService.disable("01LCREG0000000000000000001");

            assertEquals(LcComponentRegistry.STATUS_DISABLED, existing.getStatus());
        }

        @Test
        @DisplayName("disable: 已 DISABLED 幂等返回，不调用 updateById")
        void disableIdempotent() {
            LcComponentRegistry existing = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            existing.setStatus(LcComponentRegistry.STATUS_DISABLED);
            when(registryMapper.selectById(anyString())).thenReturn(existing);

            applicationService.disable("01LCREG0000000000000000001");

            verify(registryMapper, never()).updateById(org.mockito.Mockito.<LcComponentRegistry>any());
        }

        @Test
        @DisplayName("disable: DRAFT 状态不允许禁用")
        void disableWrongState() {
            LcComponentRegistry existing = buildEntity("01LCREG0000000000000000001", "TextInput", "文本输入", "INPUT");
            existing.setStatus(LcComponentRegistry.STATUS_DRAFT);
            when(registryMapper.selectById(anyString())).thenReturn(existing);

            assertThrows(BusinessConflictException.class,
                    () -> applicationService.disable("01LCREG0000000000000000001"));
        }

        @Test
        @DisplayName("publish: 不存在抛 ResourceNotFoundException")
        void publishNotFound() {
            when(registryMapper.selectById(anyString())).thenReturn(null);
            assertThrows(ResourceNotFoundException.class,
                    () -> applicationService.publish("nonexistent"));
        }
    }

    // ==================== 辅助方法 ====================

    private LcComponentRegistry buildEntity(String id, String code, String name, String category) {
        LcComponentRegistry entity = new LcComponentRegistry();
        entity.setId(id);
        entity.setTenantId("default");
        entity.setComponentCode(code);
        entity.setComponentName(name);
        entity.setComponentType(category);
        entity.setDisplayName(name);
        entity.setPlatform(LcComponentRegistry.PLATFORM_WEB);
        entity.setCategory(category);
        entity.setCompatibilityGrade(LcComponentRegistry.GRADE_STABLE);
        entity.setComponentVersion("1.0.0");
        entity.setStatus(LcComponentRegistry.STATUS_PUBLISHED);
        entity.setDeprecated(false);
        entity.setSortNo(1);
        entity.setVersion(0);
        return entity;
    }

    private SaveComponentRegistryRequest buildSaveRequest(String code, String name, String type, String displayName) {
        SaveComponentRegistryRequest req = new SaveComponentRegistryRequest();
        req.setComponentCode(code);
        req.setComponentName(name);
        req.setComponentType(type);
        req.setDisplayName(displayName);
        req.setPlatform(LcComponentRegistry.PLATFORM_WEB);
        req.setCategory(type);
        req.setCompatibilityGrade(LcComponentRegistry.GRADE_STABLE);
        req.setComponentVersion("1.0.0");
        req.setSortNo(1);
        req.setDeprecated(false);
        req.setPermissionSupport(false);
        req.setValidationSupport(false);
        req.setPropsSchema("{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}}}");
        req.setEventSchema("{\"change\":{\"description\":\"值变更\"}}");
        return req;
    }
}
