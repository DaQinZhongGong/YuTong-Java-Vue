# pdf

适用场景：创建、分析 PDF。
输入要求：content 文本；create 时可提供 title/fileName。
操作步骤：analyze 统计行数；create 用 PDFBox 生成 PDF 并上传 MinIO。
输出规范：analyze 返回摘要；create 返回 downloadUrl。
限制条件：Helvetica 仅覆盖 ASCII；中文建议使用 docx。
