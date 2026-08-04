package com.yutong.sample.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.AuthAdapter;
import com.yutong.auth.AuthContext;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.mask.FieldMaskingService;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.sample.masterdata.domain.Customer;
import com.yutong.sample.masterdata.mapper.CustomerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户主数据服务。设计来源: 18-样例业务详细设计、98-后端实现蓝图
 * 约束: 编码租户内唯一; 乐观锁; 逻辑删除。
 *
 * <p>GA2-L176: 列表查询 contactPhone 默认脱敏 (67 号文档 line 85/167
 * "contact_phone 138****8000 列表默认脱敏")。
 * 详情查询不脱敏 (67 号文档 line 85 "详情按字段权限决定"，第一版 admin 默认可见)。
 *
 * <p>GA2-DS: pageCustomers 接入 DataScope 数据权限过滤。
 * 默认 TENANT (租户隔离由 BaseEntity 保证)，对非 admin 角色追加 owner 过滤。
 * Customer 实体无 owner_user_id 字段，使用 created_by 作为 owner 字段。
 */
@Service
public class CustomerService {

    /** 客户资源编码，对齐 permissions.yaml biz:customer:* 命名。 */
    public static final String RESOURCE_CODE = "biz:customer";

    private final CustomerMapper customerMapper;
    private final AuthAdapter authAdapter;
    private final DataScopeResolver dataScopeResolver;

    public CustomerService(CustomerMapper customerMapper, AuthAdapter authAdapter,
                           DataScopeResolver dataScopeResolver) {
        this.customerMapper = customerMapper;
        this.authAdapter = authAdapter;
        this.dataScopeResolver = dataScopeResolver;
    }

    /** 分页查询客户。keyword 模糊匹配 customer_code / customer_name。列表 contactPhone 默认脱敏。 */
    public PageResult<Customer> pageCustomers(PageRequest request, String keyword, String status) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .eq(Customer::getTenantId, CurrentUserContext.getTenantId())
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Customer::getCustomerCode, keyword)
                        .or()
                        .like(Customer::getCustomerName, keyword))
                .eq(status != null && !status.isBlank(), Customer::getStatus, status)
                .orderByDesc(Customer::getCreatedTime);
        // GA2-DS: 接入 DataScope 过滤，对非 admin 角色追加 owner 过滤
        applyDataScope(wrapper, scope);
        Page<Customer> page = customerMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        // GA2-L176: 列表 contactPhone 默认脱敏 (67 号文档 line 85/167)
        page.getRecords().forEach(this::maskContactPhone);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * Customer 实体无 owner_user_id 字段，使用 created_by (BaseEntity) 作为 owner 字段。
     * - ALL/TENANT: 无附加条件 (admin/viewer)
     * - SELF: created_by = currentUserId (biz 用户)
     * - DEPT/DEPT_AND_CHILD/CUSTOM/NONE: 实体无对应 dept 字段，安全降级为 created_by = currentUserId
     */
    private void applyDataScope(LambdaQueryWrapper<Customer> wrapper, DataScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) {
            return;
        }
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        // 非 admin: 追加 owner 过滤 (Customer 无 owner_user_id，使用 created_by 作为 owner)
        wrapper.eq(Customer::getCreatedBy, userId);
    }

    /** 查询客户详情。不存在抛 ResourceNotFoundException。详情按字段权限脱敏 (67 号文档 line 85)。 */
    public Customer getCustomer(String id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new ResourceNotFoundException(ErrorCode.BIZ_CUSTOMER_NOT_FOUND, "客户不存在: " + id);
        }
        // GA2-L181: 详情 contactPhone 按字段权限脱敏 (openapi getCustomer: "敏感联系方式按字段权限脱敏")
        // 有 biz:customer:phone:view 或 admin 通配权限 → 可见原文; 否则脱敏 (viewer)
        maskContactPhoneByPermission(customer);
        return customer;
    }

    /** GA2-L176: 列表脱敏 contactPhone: 138****8000 (67 号文档 line 85) */
    private void maskContactPhone(Customer customer) {
        if (customer != null && customer.getContactPhone() != null && !customer.getContactPhone().isBlank()) {
            customer.setContactPhone(FieldMaskingService.maskPhone(customer.getContactPhone()));
        }
    }

    /** GA2-L181: 详情按字段权限脱敏 contactPhone (有 biz:customer:phone:view 或 admin 通配 → 不脱敏)。 */
    private void maskContactPhoneByPermission(Customer customer) {
        if (customer == null || customer.getContactPhone() == null || customer.getContactPhone().isBlank()) {
            return;
        }
        AuthContext ctx = authAdapter.current();
        boolean canViewPhone = ctx != null
                && (ctx.hasPermission("*") || ctx.hasPermission("biz:customer:phone:view"));
        if (!canViewPhone) {
            customer.setContactPhone(FieldMaskingService.maskPhone(customer.getContactPhone()));
        }
    }

    /** 创建客户。设置 id/tenantId/createdBy; 校验 tenant_id + customer_code 唯一。 */
    @Transactional
    public Customer createCustomer(Customer customer) {
        // 编码唯一校验 (租户内)
        LambdaQueryWrapper<Customer> dupCheck = new LambdaQueryWrapper<Customer>()
                .eq(Customer::getTenantId, CurrentUserContext.getTenantId())
                .eq(Customer::getCustomerCode, customer.getCustomerCode());
        Long count = customerMapper.selectCount(dupCheck);
        if (count != null && count > 0) {
            // GA2-L180: 错误码修正 SYS_BUSINESS_CONFLICT(SYS-409004) → BIZ_CUSTOMER_CODE_DUPLICATE(BIZ-409005)
            // 对齐 58 号文档 CT-createCustomer 契约测试预期错误码
            throw new BusinessException(ErrorCode.BIZ_CUSTOMER_CODE_DUPLICATE,
                    "客户编码已存在: " + customer.getCustomerCode());
        }

        customer.setId(IdGenerator.nextId());
        customer.setTenantId(CurrentUserContext.getTenantId());
        customer.setCreatedBy(CurrentUserContext.getUserId());
        customerMapper.insert(customer);
        return customer;
    }

    /** 更新客户。乐观锁; 编码变更时校验唯一。 */
    @Transactional
    public Customer updateCustomer(String id, Customer customer) {
        Customer existing = getCustomer(id);
        customer.setId(id);
        customer.setTenantId(existing.getTenantId());

        // 编码变更时校验租户内唯一
        if (customer.getCustomerCode() != null
                && !customer.getCustomerCode().equals(existing.getCustomerCode())) {
            LambdaQueryWrapper<Customer> dupCheck = new LambdaQueryWrapper<Customer>()
                    .eq(Customer::getTenantId, existing.getTenantId())
                    .eq(Customer::getCustomerCode, customer.getCustomerCode())
                    .ne(Customer::getId, id);
            Long count = customerMapper.selectCount(dupCheck);
            if (count != null && count > 0) {
                // GA2-L180: 错误码修正 SYS_BUSINESS_CONFLICT(SYS-409004) → BIZ_CUSTOMER_CODE_DUPLICATE(BIZ-409005)
                throw new BusinessException(ErrorCode.BIZ_CUSTOMER_CODE_DUPLICATE,
                        "客户编码已存在: " + customer.getCustomerCode());
            }
        }

        customer.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = customerMapper.updateById(customer);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.SYS_OPTIMISTIC_LOCK,
                    "客户已被其他操作更新，请刷新后重试: " + id);
        }
        return customer;
    }

    /** 逻辑删除客户。 */
    @Transactional
    public void deleteCustomer(String id) {
        getCustomer(id);
        customerMapper.deleteById(id);
    }
}
