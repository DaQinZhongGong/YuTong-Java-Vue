# xlsx

适用场景：创建、分析表格。
输入要求：content 按行，可用逗号或制表符分列。
操作步骤：analyze 统计行数；create 用 POI 生成 xlsx 并上传 MinIO。
输出规范：analyze 返回摘要；create 返回 downloadUrl。
限制条件：最多 50000 行 / 32 列。
