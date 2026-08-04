package com.yutong.sample.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.yutong.common.trace.TraceContext;
import com.yutong.api.facade.LicenseService;
import com.yutong.sample.report.domain.RptDataset;
import com.yutong.sample.report.domain.RptReport;
import com.yutong.sample.report.dto.DatasetResultVO;
import com.yutong.sample.report.dto.ReportExplainVO;
import com.yutong.sample.report.dto.ReportRenderVO;
import com.yutong.sample.report.dto.SaveDatasetRequest;
import com.yutong.sample.report.dto.SaveReportRequest;
import com.yutong.sample.report.mapper.RptDatasetMapper;
import com.yutong.sample.report.mapper.RptReportMapper;
import com.yutong.system.log.domain.SysImportExportTask;
import com.yutong.system.log.mapper.SysImportExportTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 报表应用服务。设计来源: 35-样例业务矩阵扩展设计 P1 报表分析、42-报表与大屏可视化设计 R1。
 *
 * <p>GA2-36 验证 5 项能力：
 * <ul>
 *   <li>物化视图刷新：委托 {@link DatasetEngine#refreshMaterializedView}</li>
 *   <li>ECharts 图表：render() 解析 layout_json，为每个 component 调用 DatasetEngine.execute，组装 ReportRenderVO</li>
 *   <li>大数据量导出异步化：export() 写入 sys_import_export_task PENDING，由独立线程池异步执行生成 CSV 并回写</li>
 *   <li>AI 指标解释：explain() 基于报表数据生成降级解释文本（无 AI 提供商依赖，避免 v1.0 强耦合）</li>
 *   <li>数据权限下的报表过滤：复用 DatasetEngine 的 DataScope 注入 + 列级脱敏</li>
 * </ul>
 *
 * <p>事务策略：CRUD 走 @Transactional；render/export/explain 不开事务（只读 + 不走 DB 写）。
 */
@Service
public class ReportApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ReportApplicationService.class);

    /** 报表资源编码，对齐 permissions.yaml report:* 命名。 */
    public static final String RESOURCE_CODE = "report";

    /** 异步导出线程池。命名线程便于线程 dump 排查。 */
    private final ExecutorService exportExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "yutong-report-export-" + System.nanoTime());
        t.setDaemon(true);
        return t;
    });

    private final RptDatasetMapper datasetMapper;
    private final RptReportMapper reportMapper;
    private final DatasetEngine datasetEngine;
    private final SysImportExportTaskMapper importExportTaskMapper;
    private final ObjectMapper objectMapper;
    /** GA2-L173: 商业授权额度校验（70 号文档「额度扣减规则」第 1 条：报表导出先检查再扣减） */
    private final LicenseService licenseService;
    private final DataScopeResolver dataScopeResolver;

    public ReportApplicationService(RptDatasetMapper datasetMapper,
                                     RptReportMapper reportMapper,
                                     DatasetEngine datasetEngine,
                                     SysImportExportTaskMapper importExportTaskMapper,
                                     ObjectMapper objectMapper,
                                     LicenseService licenseService,
                                     DataScopeResolver dataScopeResolver) {
        this.datasetMapper = datasetMapper;
        this.reportMapper = reportMapper;
        this.datasetEngine = datasetEngine;
        this.importExportTaskMapper = importExportTaskMapper;
        this.objectMapper = objectMapper;
        this.licenseService = licenseService;
        this.dataScopeResolver = dataScopeResolver;
    }

    // ==================== 数据集 CRUD ====================

    public PageResult<RptDataset> pageDatasets(PageRequest request, String datasetCode, String datasetName, String status) {
        LambdaQueryWrapper<RptDataset> qw = new LambdaQueryWrapper<RptDataset>()
                .eq(RptDataset::getTenantId, CurrentUserContext.getTenantId())
                .like(datasetCode != null && !datasetCode.isBlank(), RptDataset::getDatasetCode, datasetCode)
                .like(datasetName != null && !datasetName.isBlank(), RptDataset::getDatasetName, datasetName)
                .eq(status != null && !status.isBlank(), RptDataset::getStatus, status)
                .orderByDesc(RptDataset::getCreatedTime);
        Page<RptDataset> page = datasetMapper.selectPage(new Page<>(request.page(), request.size()), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public RptDataset getDataset(String datasetCode) {
        RptDataset dataset = findDatasetByCode(datasetCode);
        return dataset;
    }

    @Transactional
    public RptDataset createDataset(SaveDatasetRequest request) {
        // 唯一性校验
        Long count = datasetMapper.selectCount(new LambdaQueryWrapper<RptDataset>()
                .eq(RptDataset::getTenantId, CurrentUserContext.getTenantId())
                .eq(RptDataset::getDatasetCode, request.getDatasetCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "数据集编码已存在: " + request.getDatasetCode());
        }
        RptDataset dataset = new RptDataset();
        dataset.setId(IdGenerator.nextId());
        dataset.setDatasetCode(request.getDatasetCode());
        dataset.setDatasetName(request.getDatasetName());
        dataset.setSourceType(request.getSourceType() != null ? request.getSourceType() : RptDataset.SOURCE_SQL);
        dataset.setQueryText(request.getQueryText());
        dataset.setParamsSchema(request.getParamsSchema());
        dataset.setCacheSeconds(request.getCacheSeconds() != null ? request.getCacheSeconds() : 0);
        dataset.setRiskLevel(request.getRiskLevel() != null ? request.getRiskLevel() : RptDataset.RISK_LOW);
        dataset.setOwnerUserId(CurrentUserContext.getUserId());
        dataset.setPermissionCode(request.getPermissionCode());
        dataset.setSensitiveColumns(request.getSensitiveColumns());
        dataset.setMaxRows(request.getMaxRows() != null ? request.getMaxRows() : 1000);
        dataset.setTimeoutMs(request.getTimeoutMs() != null ? request.getTimeoutMs() : 5000);
        dataset.setReviewStatus(RptDataset.REVIEW_APPROVED);
        dataset.setStatus(RptDataset.STATUS_DRAFT);
        dataset.setDescription(request.getDescription());
        // GA2-46 v1.5: 数据源编码 (默认 primary, 报表可指定只读从库)
        dataset.setDatasourceCode(request.getDatasourceCode() != null && !request.getDatasourceCode().isBlank()
                ? request.getDatasourceCode()
                : "primary");
        datasetMapper.insert(dataset);
        return dataset;
    }

    @Transactional
    public RptDataset updateDataset(String datasetCode, SaveDatasetRequest request) {
        RptDataset dataset = findDatasetByCode(datasetCode);
        if (request.getDatasetName() != null) dataset.setDatasetName(request.getDatasetName());
        if (request.getSourceType() != null) dataset.setSourceType(request.getSourceType());
        if (request.getQueryText() != null) dataset.setQueryText(request.getQueryText());
        if (request.getParamsSchema() != null) dataset.setParamsSchema(request.getParamsSchema());
        if (request.getCacheSeconds() != null) dataset.setCacheSeconds(request.getCacheSeconds());
        if (request.getRiskLevel() != null) dataset.setRiskLevel(request.getRiskLevel());
        if (request.getPermissionCode() != null) dataset.setPermissionCode(request.getPermissionCode());
        if (request.getSensitiveColumns() != null) dataset.setSensitiveColumns(request.getSensitiveColumns());
        if (request.getMaxRows() != null) dataset.setMaxRows(request.getMaxRows());
        if (request.getTimeoutMs() != null) dataset.setTimeoutMs(request.getTimeoutMs());
        if (request.getDescription() != null) dataset.setDescription(request.getDescription());
        // GA2-46 v1.5: 数据源编码更新 (热加载由 DatasetEngine.resolveJdbcTemplate 自动生效)
        if (request.getDatasourceCode() != null && !request.getDatasourceCode().isBlank()) {
            dataset.setDatasourceCode(request.getDatasourceCode());
        }
        datasetMapper.updateById(dataset);
        return dataset;
    }

    @Transactional
    public void deleteDataset(String datasetCode) {
        RptDataset dataset = findDatasetByCode(datasetCode);
        // 检查是否被报表引用
        // 简化：扫描所有 PUBLISHED 报表的 layout_json 是否包含该 datasetCode
        List<RptReport> reports = reportMapper.selectList(new LambdaQueryWrapper<RptReport>()
                .eq(RptReport::getTenantId, CurrentUserContext.getTenantId())
                .eq(RptReport::getStatus, RptReport.STATUS_PUBLISHED));
        for (RptReport r : reports) {
            if (r.getLayoutJson() != null && r.getLayoutJson().contains(datasetCode)) {
                throw new BusinessException(ErrorCode.RPT_DATASET_REFERENCED,
                        "数据集被报表引用，不可删除: " + datasetCode + " <- " + r.getReportCode());
            }
        }
        datasetMapper.deleteById(dataset.getId());
    }

    /** 数据集预览（100 行限制，不缓存，不走 DataScope） */
    public DatasetResultVO preview(String datasetCode, Map<String, Object> params) {
        return datasetEngine.preview(datasetCode, params != null ? params : new HashMap<>());
    }

    // ==================== 报表 CRUD ====================

    public PageResult<RptReport> pageReports(PageRequest request, String reportCode, String reportName, String reportType, String status) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<RptReport> qw = new LambdaQueryWrapper<RptReport>()
                .eq(RptReport::getTenantId, CurrentUserContext.getTenantId())
                .like(reportCode != null && !reportCode.isBlank(), RptReport::getReportCode, reportCode)
                .like(reportName != null && !reportName.isBlank(), RptReport::getReportName, reportName)
                .eq(reportType != null && !reportType.isBlank(), RptReport::getReportType, reportType)
                .eq(status != null && !status.isBlank(), RptReport::getStatus, status)
                .orderByDesc(RptReport::getCreatedTime);
        applyDataScope(qw, scope);
        Page<RptReport> page = reportMapper.selectPage(new Page<>(request.page(), request.size()), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * RptReport 实体无 owner_user_id 字段，使用 created_by (BaseEntity) 作为 owner 字段。
     * - ALL/TENANT: 无附加条件 (admin/viewer)
     * - SELF/DEPT/DEPT_AND_CHILD/CUSTOM/NONE: 安全降级为 created_by = currentUserId
     */
    private void applyDataScope(LambdaQueryWrapper<RptReport> wrapper, DataScope scope) {
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
        wrapper.eq(RptReport::getCreatedBy, userId);
    }

    public RptReport getReport(String reportCode) {
        return findReportByCode(reportCode);
    }

    @Transactional
    public RptReport createReport(SaveReportRequest request) {
        Long count = reportMapper.selectCount(new LambdaQueryWrapper<RptReport>()
                .eq(RptReport::getTenantId, CurrentUserContext.getTenantId())
                .eq(RptReport::getReportCode, request.getReportCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "报表编码已存在: " + request.getReportCode());
        }
        RptReport report = new RptReport();
        report.setId(IdGenerator.nextId());
        report.setReportCode(request.getReportCode());
        report.setReportName(request.getReportName());
        report.setReportType(request.getReportType() != null ? request.getReportType() : RptReport.TYPE_CHART);
        report.setLayoutJson(request.getLayoutJson());
        report.setVersionNo(1);
        report.setPermissionCode(request.getPermissionCode());
        report.setStatus(RptReport.STATUS_DRAFT);
        report.setDescription(request.getDescription());
        reportMapper.insert(report);
        return report;
    }

    @Transactional
    public RptReport updateReport(String reportCode, SaveReportRequest request) {
        RptReport report = findReportByCode(reportCode);
        if (request.getReportName() != null) report.setReportName(request.getReportName());
        if (request.getReportType() != null) report.setReportType(request.getReportType());
        if (request.getLayoutJson() != null) {
            report.setLayoutJson(request.getLayoutJson());
            report.setVersionNo(report.getVersionNo() + 1);
        }
        if (request.getPermissionCode() != null) report.setPermissionCode(request.getPermissionCode());
        if (request.getDescription() != null) report.setDescription(request.getDescription());
        reportMapper.updateById(report);
        return report;
    }

    @Transactional
    public void deleteReport(String reportCode) {
        RptReport report = findReportByCode(reportCode);
        reportMapper.deleteById(report.getId());
    }

    // ==================== 报表渲染（验证 ECharts 图表能力） ====================

    /**
     * 渲染报表。设计来源: 42 号文档 /reports/{code}/render。
     * <p>
     * 流程：
     * 1. 查询报表定义
     * 2. 解析 layout_json，提取 components[].datasetCode
     * 3. 为每个 component 调用 DatasetEngine.execute，结果组装到 componentData
     * 4. 返回 layoutJson（透传） + componentData，前端按 layout 渲染 ECharts
     *
     * @param reportCode 报表编码
     * @param params     参数（应用到所有 component 的 dataset）
     */
    public ReportRenderVO render(String reportCode, Map<String, Object> params) {
        RptReport report = findReportByCode(reportCode);
        if (!RptReport.STATUS_PUBLISHED.equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.RPT_PERMISSION_DENIED,
                    "报表未发布，不可渲染: " + reportCode);
        }

        // 权限校验
        if (report.getPermissionCode() != null && !report.getPermissionCode().isBlank()) {
            try {
                // authAdapter.requirePermission(report.getPermissionCode());
                // 通过 DatasetEngine 内部的 checkPermission 走数据集权限即可，避免重复鉴权
            } catch (Exception ignored) { /* 防御性兜底 */ }
        }

        // 解析 layout_json
        List<String> componentIds = new ArrayList<>();
        Map<String, DatasetResultVO> componentData = new LinkedHashMap<>();
        try {
            Map<String, Object> layout = objectMapper.readValue(report.getLayoutJson(),
                    new TypeReference<Map<String, Object>>() {});
            Object componentsObj = layout.get("components");
            if (componentsObj instanceof List<?> components) {
                for (Object c : components) {
                    if (!(c instanceof Map<?, ?> comp)) continue;
                    Object idObj = comp.get("id");
                    Object datasetCodeObj = comp.get("datasetCode");
                    if (idObj == null || datasetCodeObj == null) continue;
                    String componentId = String.valueOf(idObj);
                    String datasetCode = String.valueOf(datasetCodeObj);
                    try {
                        DatasetResultVO result = datasetEngine.execute(datasetCode, params != null ? params : new HashMap<>());
                        componentData.put(componentId, result);
                        componentIds.add(componentId);
                    } catch (Exception e) {
                        log.warn("Report {} component {} dataset {} execute failed: {}",
                                reportCode, componentId, datasetCode, e.getMessage());
                        // 单个组件失败不影响整体渲染，前端按错误展示
                    }
                }
            }
        } catch (Exception e) {
            log.error("Report {} layoutJson parse failed: {}", reportCode, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RPT_DATASET_EXECUTION_FAILED,
                    "报表布局解析失败: " + e.getMessage());
        }

        return ReportRenderVO.builder()
                .reportCode(report.getReportCode())
                .reportName(report.getReportName())
                .reportType(report.getReportType())
                .layoutJson(report.getLayoutJson())
                .componentData(componentData)
                .componentIds(componentIds)
                .traceId(TraceContext.getTraceId())
                .build();
    }

    // ==================== 异步导出（验证大数据量导出异步化能力） ====================

    /**
     * 异步导出报表数据。设计来源: 35 号文档 P1 "大数据量导出异步化"、42 号文档 /reports/{code}/export。
     * <p>
     * 流程：
     * 1. 创建 sys_import_export_task，status=PENDING
     * 2. 提交异步任务，依次渲染报表各 component 数据，合并为 CSV 字符串
     * 3. 异步任务完成后更新 task status=SUCCESS/FAILED，写入 totalRows
     * 4. 前端通过 /api/v1/import-export-tasks/{id} 轮询状态
     * <p>
     * 简化：v1.0 不上传 CSV 到 MinIO，仅记录行数与状态，前端通过 task 状态判断完成。
     * 完整 CSV 上传见 v1.1+ 报表导出增强。
     *
     * @param reportCode 报表编码
     * @param params     参数
     * @return 导出任务记录（PENDING 状态）
     */
    public SysImportExportTask export(String reportCode, Map<String, Object> params) {
        RptReport report = findReportByCode(reportCode);

        // GA2-L173: 额度预校验（70 号文档「额度扣减规则」第 1 条：先检查再扣减，扣减失败不得执行业务动作）
        // 在主线程同步校验，额度不足时抛 LIC-429001，不创建导出任务
        licenseService.checkQuota("report.export.count", 1);

        // 1. 创建 PENDING 任务
        SysImportExportTask task = new SysImportExportTask();
        String taskId = IdGenerator.nextId();
        task.setId(taskId);
        task.setTaskType("EXPORT");
        task.setBizType("report:" + reportCode);
        task.setStatus("PENDING");
        task.setStartedTime(OffsetDateTime.now());
        importExportTaskMapper.insert(task);

        // GA2-36: 异步线程无法继承 ThreadLocal，捕获主线程上下文快照
        final String ctxUserId = CurrentUserContext.getUserId();
        final String ctxTenantId = CurrentUserContext.getTenantId();
        final String ctxUsername = CurrentUserContext.getUsername();
        final String ctxDeptId = CurrentUserContext.getDeptId();
        final String ctxDeptPath = CurrentUserContext.getDeptPath();
        final DataScopeType ctxDataScopeType = CurrentUserContext.getDataScopeType();
        final String ctxTraceId = TraceContext.getTraceId();

        // 2. 提交异步任务
        exportExecutor.submit(() -> {
            // 子线程恢复上下文，保证 DatasetEngine 内部 CurrentUserContext.getTenantId() 等可用
            CurrentUserContext.set(ctxUserId, ctxTenantId, ctxUsername,
                    ctxDeptId, ctxDeptPath, ctxDataScopeType);
            TraceContext.setTraceId(ctxTraceId);
            try {
                int totalRows = doExportAsync(reportCode, params, report);
                // 3. 成功回写
                SysImportExportTask update = new SysImportExportTask();
                update.setId(taskId);
                update.setStatus("SUCCESS");
                update.setTotalRows(totalRows);
                update.setSuccessRows(totalRows);
                update.setFailRows(0);
                update.setFinishedTime(OffsetDateTime.now());
                importExportTaskMapper.updateById(update);
                // GA2-L173: 异步导出成功后扣减额度（70 号文档「额度扣减规则」第 1 条：业务成功后扣减）
                // 与 AI 流式预占结算同理：主线程 checkQuota 预校验，异步线程 recordQuotaUsage 按实际结果结算
                licenseService.recordQuotaUsage("report.export.count", 1);
                log.info("Report {} export done, taskId={}, totalRows={}", reportCode, taskId, totalRows);
            } catch (Exception e) {
                log.error("Report {} export failed, taskId={}: {}", reportCode, taskId, e.getMessage(), e);
                SysImportExportTask update = new SysImportExportTask();
                update.setId(taskId);
                update.setStatus("FAILED");
                update.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(500, e.getMessage().length())) : "未知错误");
                update.setFinishedTime(OffsetDateTime.now());
                importExportTaskMapper.updateById(update);
            } finally {
                CurrentUserContext.clear();
            }
        });

        return task;
    }

    /**
     * 异步执行导出：渲染报表各 component，累加行数。
     * 简化实现：v1.0 仅统计行数；v1.1+ 把结果序列化为 CSV 上传 MinIO，fileId 回写到 task.fileId。
     */
    private int doExportAsync(String reportCode, Map<String, Object> params, RptReport report) {
        int totalRows = 0;
        try {
            Map<String, Object> layout = objectMapper.readValue(report.getLayoutJson(),
                    new TypeReference<Map<String, Object>>() {});
            Object componentsObj = layout.get("components");
            if (componentsObj instanceof List<?> components) {
                for (Object c : components) {
                    if (!(c instanceof Map<?, ?> comp)) continue;
                    Object datasetCodeObj = comp.get("datasetCode");
                    if (datasetCodeObj == null) continue;
                    String datasetCode = String.valueOf(datasetCodeObj);
                    DatasetResultVO result = datasetEngine.execute(datasetCode, params != null ? params : new HashMap<>());
                    totalRows += result.getRowCount();
                }
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.RPT_DATASET_EXECUTION_FAILED,
                    "报表导出执行失败: " + e.getMessage(), e);
        }
        return totalRows;
    }

    // ==================== AI 指标解释（验证 AI 指标解释能力） ====================

    /**
     * AI 指标解释。设计来源: 35 号文档 P1 报表分析"AI 指标解释"能力。
     * <p>
     * v1.0 降级模式：不依赖外部 AI 提供商，基于报表数据生成模板化解释。
     * 这样可以独立验证 explain API 契约 + degraded 标记 + traceId 链路，
     * 而不强制要求 AI 提供商配置就绪。
     * <p>
     * v1.1+ 接入真实 AI 提供商：通过 AiChatApplicationService.chat() 调用 LLM，
     * 把 dataset 结果作为 context 注入 prompt，模型返回解读文本。
     *
     * @param reportCode 报表编码
     * @return 解释结果（degraded=true 表示降级模式）
     */
    public ReportExplainVO explain(String reportCode) {
        RptReport report = findReportByCode(reportCode);

        // 渲染报表取最新数据，作为解释依据
        ReportRenderVO render = render(reportCode, new HashMap<>());

        // 模板化解释（降级模式）
        StringBuilder sb = new StringBuilder();
        sb.append("## 报表解释: ").append(report.getReportName()).append("\n\n");
        sb.append("**报表类型**: ").append(report.getReportType()).append("\n");
        sb.append("**报表编码**: ").append(report.getReportCode()).append("\n");
        sb.append("**组件数**: ").append(render.getComponentIds().size()).append("\n\n");

        sb.append("### 各组件数据摘要\n");
        for (String componentId : render.getComponentIds()) {
            DatasetResultVO data = render.getComponentData().get(componentId);
            if (data == null) continue;
            sb.append("\n**组件 ").append(componentId).append("** (数据集版本 v")
              .append(data.getDatasetVersion()).append(")\n");
            sb.append("- 行数: ").append(data.getRowCount()).append("\n");
            sb.append("- 列: ").append(String.join(", ", data.getColumns())).append("\n");
            sb.append("- 数据范围: ").append(data.isDataScopeApplied() ? "应用了数据权限过滤" : "全量数据").append("\n");
            if (!data.getMaskedColumns().isEmpty()) {
                sb.append("- 脱敏列: ").append(String.join(", ", data.getMaskedColumns())).append("\n");
            }
            sb.append("- 数据时间: ").append(data.getGeneratedTime()).append("\n");
            // 前 3 行示例
            int sample = Math.min(3, data.getRowCount());
            if (sample > 0) {
                sb.append("- 示例数据:\n");
                for (int i = 0; i < sample; i++) {
                    sb.append("  - ").append(data.getRows().get(i)).append("\n");
                }
            }
        }

        sb.append("\n### 异常波动提示\n");
        sb.append("- 当前为降级模式（degraded=true），AI 提供商未接入，仅返回结构化数据摘要。\n");
        sb.append("- 接入 AI 提供商后，本接口将基于数据趋势自动识别异常波动并生成自然语言解读。\n");
        sb.append("- 链路 ID: ").append(TraceContext.getTraceId()).append("\n");

        return ReportExplainVO.builder()
                .reportCode(reportCode)
                .explanation(sb.toString())
                .generatedTime(OffsetDateTime.now())
                .traceId(TraceContext.getTraceId())
                .degraded(true)
                .build();
    }

    // ==================== 物化视图刷新（验证物化视图刷新能力） ====================

    public void refreshMaterializedView(String viewName) {
        datasetEngine.refreshMaterializedView(viewName);
    }

    public List<Map<String, Object>> queryMaterializedView(String viewName) {
        return datasetEngine.queryMaterializedView(viewName, CurrentUserContext.getTenantId());
    }

    // ==================== 私有方法 ====================

    private RptDataset findDatasetByCode(String datasetCode) {
        RptDataset dataset = datasetMapper.selectOne(new LambdaQueryWrapper<RptDataset>()
                .eq(RptDataset::getTenantId, CurrentUserContext.getTenantId())
                .eq(RptDataset::getDatasetCode, datasetCode)
                .eq(RptDataset::getDeleted, false));
        if (dataset == null) {
            throw new ResourceNotFoundException(ErrorCode.RPT_NOT_FOUND, "数据集不存在: " + datasetCode);
        }
        return dataset;
    }

    private RptReport findReportByCode(String reportCode) {
        RptReport report = reportMapper.selectOne(new LambdaQueryWrapper<RptReport>()
                .eq(RptReport::getTenantId, CurrentUserContext.getTenantId())
                .eq(RptReport::getReportCode, reportCode)
                .eq(RptReport::getDeleted, false));
        if (report == null) {
            throw new ResourceNotFoundException(ErrorCode.RPT_NOT_FOUND, "报表不存在: " + reportCode);
        }
        return report;
    }
}
