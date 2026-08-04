package com.yutong.system.log.service;

/**
 * 导入导出业务工作函数的返回结果。
 * 设计来源: 52-后端服务分工与接口实现详设 / 58-后端API逐接口任务清单
 *
 * <p>runner 负责创建任务记录、写 sys_job_log、更新任务状态；业务工作函数只需返回
 * 行数统计、生成的文件 ID (导出文件 / 导入错误报告) 与可选的汇总消息。
 *
 * <p>字段约定:
 * <ul>
 *   <li>导入: {@code outputFileId} 为错误报告文件 ID (无错误时为 null)；{@code errorMessage} 为汇总信息</li>
 *   <li>导出: {@code outputFileId} 为生成的导出文件 ID；{@code errorMessage} 一般为 null</li>
 * </ul>
 */
public record ImportExportResult(
        int totalRows,
        int successRows,
        int failRows,
        String outputFileId,
        String errorMessage
) {
    /** 构造导入结果 (含可选错误报告文件 ID)。 */
    public static ImportExportResult ofImport(int total, int success, int fail,
                                              String errorReportFileId, String errorMessage) {
        return new ImportExportResult(total, success, fail, errorReportFileId, errorMessage);
    }

    /** 构造导出结果。 */
    public static ImportExportResult ofExport(int total, String outputFileId) {
        return new ImportExportResult(total, total, 0, outputFileId, null);
    }
}
