package com.yutong.sample.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.sample.masterdata.domain.Product;
import com.yutong.sample.masterdata.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品主数据服务。设计来源: 18-样例业务详细设计、98-后端实现蓝图
 * 约束: 编码租户内唯一; 乐观锁; 逻辑删除。
 *
 * <p>GA2-DS: pageProducts 接入 DataScope 数据权限过滤。
 * 默认 TENANT (租户隔离由 BaseEntity 保证)，对非 admin 角色追加 owner 过滤。
 * Product 实体无 owner_user_id 字段，使用 created_by 作为 owner 字段。
 */
@Service
public class ProductService {

    /** 商品资源编码，对齐 permissions.yaml biz:product:* 命名。 */
    public static final String RESOURCE_CODE = "biz:product";

    private final ProductMapper productMapper;
    private final DataScopeResolver dataScopeResolver;

    public ProductService(ProductMapper productMapper, DataScopeResolver dataScopeResolver) {
        this.productMapper = productMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    /** 分页查询商品。keyword 模糊匹配 product_code / product_name。 */
    public PageResult<Product> pageProducts(PageRequest request, String keyword, String status) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getTenantId, CurrentUserContext.getTenantId())
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Product::getProductCode, keyword)
                        .or()
                        .like(Product::getProductName, keyword))
                .eq(status != null && !status.isBlank(), Product::getStatus, status)
                .orderByDesc(Product::getCreatedTime);
        // GA2-DS: 接入 DataScope 过滤，对非 admin 角色追加 owner 过滤
        applyDataScope(wrapper, scope);
        Page<Product> page = productMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * Product 实体无 owner_user_id 字段，使用 created_by (BaseEntity) 作为 owner 字段。
     * - ALL/TENANT: 无附加条件 (admin/viewer)
     * - SELF: created_by = currentUserId (biz 用户)
     * - DEPT/DEPT_AND_CHILD/CUSTOM/NONE: 实体无对应 dept 字段，安全降级为 created_by = currentUserId
     */
    private void applyDataScope(LambdaQueryWrapper<Product> wrapper, DataScope scope) {
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
        // 非 admin: 追加 owner 过滤 (Product 无 owner_user_id，使用 created_by 作为 owner)
        wrapper.eq(Product::getCreatedBy, userId);
    }

    /** 查询商品详情。不存在抛 ResourceNotFoundException。 */
    public Product getProduct(String id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new ResourceNotFoundException(ErrorCode.BIZ_PRODUCT_NOT_FOUND, "商品不存在: " + id);
        }
        return product;
    }

    /** 创建商品。设置 id/tenantId/createdBy; 校验 tenant_id + product_code 唯一。 */
    @Transactional
    public Product createProduct(Product product) {
        // 编码唯一校验 (租户内)
        LambdaQueryWrapper<Product> dupCheck = new LambdaQueryWrapper<Product>()
                .eq(Product::getTenantId, CurrentUserContext.getTenantId())
                .eq(Product::getProductCode, product.getProductCode());
        Long count = productMapper.selectCount(dupCheck);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.BIZ_PRODUCT_CODE_DUPLICATE,
                    "商品编码已存在: " + product.getProductCode());
        }

        product.setId(IdGenerator.nextId());
        product.setTenantId(CurrentUserContext.getTenantId());
        product.setCreatedBy(CurrentUserContext.getUserId());
        productMapper.insert(product);
        return product;
    }

    /** 更新商品。乐观锁; 编码变更时校验唯一。 */
    @Transactional
    public Product updateProduct(String id, Product product) {
        Product existing = getProduct(id);
        product.setId(id);
        product.setTenantId(existing.getTenantId());

        // 编码变更时校验租户内唯一
        if (product.getProductCode() != null
                && !product.getProductCode().equals(existing.getProductCode())) {
            LambdaQueryWrapper<Product> dupCheck = new LambdaQueryWrapper<Product>()
                    .eq(Product::getTenantId, existing.getTenantId())
                    .eq(Product::getProductCode, product.getProductCode())
                    .ne(Product::getId, id);
            Long count = productMapper.selectCount(dupCheck);
            if (count != null && count > 0) {
                throw new BusinessException(ErrorCode.BIZ_PRODUCT_CODE_DUPLICATE,
                        "商品编码已存在: " + product.getProductCode());
            }
        }

        product.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = productMapper.updateById(product);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.SYS_OPTIMISTIC_LOCK,
                    "商品已被其他操作更新，请刷新后重试: " + id);
        }
        return product;
    }

    /** 逻辑删除商品。 */
    @Transactional
    public void deleteProduct(String id) {
        getProduct(id);
        productMapper.deleteById(id);
    }
}
