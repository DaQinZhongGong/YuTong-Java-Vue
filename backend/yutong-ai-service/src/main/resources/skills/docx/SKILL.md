# docx

适用场景：创建、分析 Word 文档。
输入要求：content 文本；create 时可提供 title/fileName。
操作步骤：analyze 统计字词；create 用 POI 生成 docx 并上传 MinIO。
输出规范：analyze 返回摘要；create 返回 downloadUrl。
限制条件：不执行外部命令；内容上限 200000 字符。
