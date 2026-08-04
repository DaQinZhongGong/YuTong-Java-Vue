package com.yutong.lowcode.meta.service;

import com.yutong.common.exception.BusinessConflictException;
import com.yutong.lowcode.meta.domain.LcEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 低代码状态机领域服务单元测试。设计来源: 14-低代码平台设计 状态机
 *
 * 实体: DRAFT → PUBLISHED → DISABLED；DISABLED → DRAFT (RE_ENABLE)；PUBLISHED/DRAFT → DISABLED
 * 页面: DRAFT → PUBLISHED；PUBLISHED → DRAFT (ROLLBACK)
 * 生成任务: PENDING → RUNNING → SUCCESS/FAILED/CONFLICT；PENDING/RUNNING → CANCELLED
 * 主键约束: primary_flag=true 时只允许 field_code=id, data_type=STRING
 */
@DisplayName("低代码状态机领域服务")
class LcDomainServiceTest {

    private final LcDomainService domainService = new LcDomainService();

    // ==================== 实体状态机 ====================

    @Nested
    @DisplayName("实体: nextStatus 动作映射")
    class EntityNextStatus {

        @Test
        @DisplayName("PUBLISH → PUBLISHED")
        void publishReturnsPublished() {
            assertEquals(LcEntity.STATUS_PUBLISHED, domainService.entityNextStatus("PUBLISH"));
        }

        @Test
        @DisplayName("DISABLE → DISABLED")
        void disableReturnsDisabled() {
            assertEquals(LcEntity.STATUS_DISABLED, domainService.entityNextStatus("DISABLE"));
        }

        @Test
        @DisplayName("RE_ENABLE → DRAFT")
        void reEnableReturnsDraft() {
            assertEquals(LcEntity.STATUS_DRAFT, domainService.entityNextStatus("RE_ENABLE"));
        }

        @Test
        @DisplayName("未知动作抛异常")
        void unknownActionThrows() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.entityNextStatus("UNKNOWN"));
        }

        @Test
        @DisplayName("null 动作抛异常")
        void nullActionThrows() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.entityNextStatus(null));
        }
    }

    @Nested
    @DisplayName("实体: validateEntityTransition 合法流转")
    class EntityLegalTransitions {

        @Test
        @DisplayName("DRAFT + PUBLISH 合法")
        void draftToPublished() {
            assertDoesNotThrow(() ->
                    domainService.validateEntityTransition(LcEntity.STATUS_DRAFT, "PUBLISH"));
        }

        @Test
        @DisplayName("PUBLISHED + DISABLE 合法")
        void publishedToDisabled() {
            assertDoesNotThrow(() ->
                    domainService.validateEntityTransition(LcEntity.STATUS_PUBLISHED, "DISABLE"));
        }

        @Test
        @DisplayName("DRAFT + DISABLE 合法 (草稿可直接禁用)")
        void draftToDisabled() {
            assertDoesNotThrow(() ->
                    domainService.validateEntityTransition(LcEntity.STATUS_DRAFT, "DISABLE"));
        }

        @Test
        @DisplayName("DISABLED + RE_ENABLE 合法")
        void disabledToDraft() {
            assertDoesNotThrow(() ->
                    domainService.validateEntityTransition(LcEntity.STATUS_DISABLED, "RE_ENABLE"));
        }
    }

    @Nested
    @DisplayName("实体: validateEntityTransition 非法流转")
    class EntityIllegalTransitions {

        @Test
        @DisplayName("PUBLISHED + PUBLISH 非法 (已发布不可重复发布)")
        void publishedPublishIllegal() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.validateEntityTransition(LcEntity.STATUS_PUBLISHED, "PUBLISH"));
        }

        @Test
        @DisplayName("DISABLED + PUBLISH 非法 (禁用态不可直接发布)")
        void disabledPublishIllegal() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.validateEntityTransition(LcEntity.STATUS_DISABLED, "PUBLISH"));
        }

        @Test
        @DisplayName("DISABLED + DISABLE 非法")
        void disabledDisableIllegal() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.validateEntityTransition(LcEntity.STATUS_DISABLED, "DISABLE"));
        }

        @Test
        @DisplayName("DRAFT + RE_ENABLE 非法 (草稿态无需重新启用)")
        void draftReEnableIllegal() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.validateEntityTransition(LcEntity.STATUS_DRAFT, "RE_ENABLE"));
        }

        @Test
        @DisplayName("PUBLISHED + RE_ENABLE 非法")
        void publishedReEnableIllegal() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.validateEntityTransition(LcEntity.STATUS_PUBLISHED, "RE_ENABLE"));
        }
    }

    // ==================== 页面状态机 ====================

    @Nested
    @DisplayName("页面: pageNextStatus 动作映射")
    class PageNextStatus {

        @Test
        @DisplayName("PUBLISH → PUBLISHED")
        void publishReturnsPublished() {
            assertEquals(LcEntity.STATUS_PUBLISHED, domainService.pageNextStatus("PUBLISH"));
        }

        @Test
        @DisplayName("ROLLBACK → DRAFT")
        void rollbackReturnsDraft() {
            assertEquals(LcEntity.STATUS_DRAFT, domainService.pageNextStatus("ROLLBACK"));
        }

        @Test
        @DisplayName("未知动作抛异常")
        void unknownActionThrows() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.pageNextStatus("UNKNOWN"));
        }
    }

    @Nested
    @DisplayName("页面: validatePageTransition 合法/非法")
    class PageTransitions {

        @Test
        @DisplayName("DRAFT + PUBLISH 合法")
        void draftToPublishedLegal() {
            assertDoesNotThrow(() ->
                    domainService.validatePageTransition("DRAFT", "PUBLISH"));
        }

        @Test
        @DisplayName("PUBLISHED + ROLLBACK 合法")
        void publishedRollbackLegal() {
            assertDoesNotThrow(() ->
                    domainService.validatePageTransition("PUBLISHED", "ROLLBACK"));
        }

        @Test
        @DisplayName("PUBLISHED + PUBLISH 非法")
        void publishedPublishIllegal() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.validatePageTransition("PUBLISHED", "PUBLISH"));
        }

        @Test
        @DisplayName("DRAFT + ROLLBACK 非法 (草稿态不可回滚)")
        void draftRollbackIllegal() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.validatePageTransition("DRAFT", "ROLLBACK"));
        }
    }

    // ==================== 生成任务状态机 ====================

    @Nested
    @DisplayName("生成任务: taskNextStatus 动作映射")
    class TaskNextStatus {

        @Test
        @DisplayName("START → RUNNING")
        void startReturnsRunning() {
            assertEquals("RUNNING", domainService.taskNextStatus("START"));
        }

        @Test
        @DisplayName("MARK_SUCCESS → SUCCESS")
        void markSuccessReturnsSuccess() {
            assertEquals("SUCCESS", domainService.taskNextStatus("MARK_SUCCESS"));
        }

        @Test
        @DisplayName("MARK_FAILED → FAILED")
        void markFailedReturnsFailed() {
            assertEquals("FAILED", domainService.taskNextStatus("MARK_FAILED"));
        }

        @Test
        @DisplayName("MARK_CONFLICT → CONFLICT")
        void markConflictReturnsConflict() {
            assertEquals("CONFLICT", domainService.taskNextStatus("MARK_CONFLICT"));
        }

        @Test
        @DisplayName("CANCEL → CANCELLED")
        void cancelReturnsCancelled() {
            assertEquals("CANCELLED", domainService.taskNextStatus("CANCEL"));
        }
    }

    @Nested
    @DisplayName("生成任务: validateTaskTransition 合法/非法")
    class TaskTransitions {

        @Test
        @DisplayName("PENDING + START 合法")
        void pendingStartLegal() {
            assertDoesNotThrow(() -> domainService.validateTaskTransition("PENDING", "START"));
        }

        @Test
        @DisplayName("RUNNING + MARK_SUCCESS 合法")
        void runningMarkSuccessLegal() {
            assertDoesNotThrow(() -> domainService.validateTaskTransition("RUNNING", "MARK_SUCCESS"));
        }

        @Test
        @DisplayName("RUNNING + MARK_FAILED 合法")
        void runningMarkFailedLegal() {
            assertDoesNotThrow(() -> domainService.validateTaskTransition("RUNNING", "MARK_FAILED"));
        }

        @Test
        @DisplayName("RUNNING + MARK_CONFLICT 合法")
        void runningMarkConflictLegal() {
            assertDoesNotThrow(() -> domainService.validateTaskTransition("RUNNING", "MARK_CONFLICT"));
        }

        @Test
        @DisplayName("PENDING + CANCEL 合法")
        void pendingCancelLegal() {
            assertDoesNotThrow(() -> domainService.validateTaskTransition("PENDING", "CANCEL"));
        }

        @Test
        @DisplayName("RUNNING + CANCEL 合法")
        void runningCancelLegal() {
            assertDoesNotThrow(() -> domainService.validateTaskTransition("RUNNING", "CANCEL"));
        }

        @Test
        @DisplayName("PENDING + MARK_SUCCESS 非法 (未运行不可标记成功)")
        void pendingMarkSuccessIllegal() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.validateTaskTransition("PENDING", "MARK_SUCCESS"));
        }

        @Test
        @DisplayName("SUCCESS + START 非法 (终态不可再启动)")
        void successStartIllegal() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.validateTaskTransition("SUCCESS", "START"));
        }

        @Test
        @DisplayName("SUCCESS + CANCEL 非法 (终态不可取消)")
        void successCancelIllegal() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.validateTaskTransition("SUCCESS", "CANCEL"));
        }

        @Test
        @DisplayName("CANCELLED + START 非法 (已取消不可重启)")
        void cancelledStartIllegal() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.validateTaskTransition("CANCELLED", "START"));
        }
    }

    // ==================== 主键约束 ====================

    @Nested
    @DisplayName("主键字段约束 validatePrimaryKey")
    class PrimaryKeyValidation {

        @Test
        @DisplayName("primary_flag=true + field_code=id + data_type=STRING 合法")
        void validPrimaryKey() {
            assertDoesNotThrow(() ->
                    domainService.validatePrimaryKey("id", "STRING", Boolean.TRUE));
        }

        @Test
        @DisplayName("primary_flag=false 任意 field_code/data_type 合法")
        void nonPrimaryKey() {
            assertDoesNotThrow(() ->
                    domainService.validatePrimaryKey("name", "STRING", Boolean.FALSE));
            assertDoesNotThrow(() ->
                    domainService.validatePrimaryKey("amount", "DECIMAL", null));
        }

        @Test
        @DisplayName("primary_flag=true + field_code != id 抛异常")
        void primaryKeyWrongCodeThrows() {
            BusinessConflictException ex = assertThrows(BusinessConflictException.class,
                    () -> domainService.validatePrimaryKey("uid", "STRING", Boolean.TRUE));
            assertTrue(ex.getMessage().contains("field_code=id"));
        }

        @Test
        @DisplayName("primary_flag=true + data_type != STRING 抛异常")
        void primaryKeyWrongTypeThrows() {
            BusinessConflictException ex = assertThrows(BusinessConflictException.class,
                    () -> domainService.validatePrimaryKey("id", "DECIMAL", Boolean.TRUE));
            assertTrue(ex.getMessage().contains("data_type=STRING"));
        }
    }
}
