-- ============================================================
-- 新增免费/公益 LLM API 供应商补丁
-- 本批次新增：9 家（第二批）
--             id 01JYYDEMOAIPROV000076 ~ 01JYYDEMOAIPROV000084，priority 240 ~ 248
-- 补丁累计：19 家
--           第一批 10 家（id 01JYYDEMOAIPROV000066 ~ 01JYYDEMOAIPROV000075，priority 230 ~ 239）
--           第二批  9 家（id 01JYYDEMOAIPROV000076 ~ 01JYYDEMOAIPROV000084，priority 240 ~ 248）
-- 来源：市场调研与官方/社区公开文档
--        - 国际免费额度：DeepInfra、Venice AI
--        - 国内模型聚合：DMXAPI
--        - 本地/私有部署：KoboldCPP、llamafile、LocalAI、FastChat、TabbyAPI、Aphrodite Engine
-- 生成日期：2026-07-27
-- 验证状态：所有 endpoint 与 model code 均来自官方文档或社区验证；
--           默认 enabled=false，需管理员补 Key 或完成本地部署后启用。
-- qwenpaw 说明：已在 R__seed_demo_data.sql 01JYYDEMOAIPROV000002 中维护，
--                本次不再重复创建；如使用 QwenPaw 本地部署，可直接引用该记录
--                并修改 endpoint / apiKey。
-- 原文件说明：未修改 R__seed_demo_data.sql。
-- ============================================================

INSERT INTO ai_provider (id, tenant_id, provider_code, provider_name, endpoint, api_key_ref, model_list_json, protocol, enabled, priority, timeout_ms, rate_limit_per_min, created_time)
VALUES
  -- 国内免费额度/需 Key
  ('01JYYDEMOAIPROV000066', 'default', 'gitee-ai', 'Gitee AI / 模力方舟', 'https://ai.gitee.com/v1', '{"apiKey":""}', '[{"code":"Qwen2.5-72B-Instruct","name":"Qwen2.5 72B Instruct（免费额度/需 Key）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"DeepSeek-V3","name":"DeepSeek-V3（免费额度/需 Key）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"glm-4-9b-chat","name":"GLM-4-9B Chat（免费额度/需 Key）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 230, 60000, 30, now() - interval '1 day'),
  -- 国际免费额度/需 Key
  ('01JYYDEMOAIPROV000067', 'default', 'shuttleai', 'ShuttleAI', 'https://api.shuttleai.com/v1', '{"apiKey":""}', '[{"code":"gpt-oss-20b","name":"GPT-OSS 20B（免费额度/需 Key）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"gpt-oss-120b","name":"GPT-OSS 120B（免费额度/需 Key）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 231, 60000, 30, now() - interval '1 day'),
  -- 本地/私有部署：完全免费，需用户自行启动服务
  ('01JYYDEMOAIPROV000068', 'default', 'lm-studio', 'LM Studio（本地）', 'http://host.docker.internal:1234/v1', '{"apiKey":""}', '[{"code":"qwen2.5-7b-instruct","name":"Qwen2.5 7B Instruct（本地部署/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（本地部署/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"qwen2.5-72b-instruct","name":"Qwen2.5 72B Instruct（本地部署/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 232, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000069', 'default', 'jan', 'Jan（本地）', 'http://host.docker.internal:1337/v1', '{"apiKey":""}', '[{"code":"qwen2.5-7b-instruct","name":"Qwen2.5 7B Instruct（本地部署/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（本地部署/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 233, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000070', 'default', 'vllm-local', 'vLLM（本地）', 'http://host.docker.internal:8000/v1', '{"apiKey":""}', '[{"code":"Qwen/Qwen2.5-7B-Instruct","name":"Qwen2.5 7B Instruct（本地部署/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"meta-llama/Meta-Llama-3.1-8B-Instruct","name":"Llama 3.1 8B Instruct（本地部署/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 234, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000071', 'default', 'xinference', 'Xinference（本地）', 'http://host.docker.internal:9997/v1', '{"apiKey":""}', '[{"code":"qwen2.5-instruct","name":"Qwen2.5 Instruct（本地部署/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（本地部署/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 235, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000072', 'default', 'llama-cpp', 'llama.cpp server（本地）', 'http://host.docker.internal:8080/v1', '{"apiKey":""}', '[{"code":"qwen2.5-7b-instruct","name":"Qwen2.5 7B Instruct（本地部署/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（本地部署/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 236, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000073', 'default', 'huggingface-tgi', 'Hugging Face TGI（本地）', 'http://host.docker.internal:8080/v1', '{"apiKey":""}', '[{"code":"Qwen/Qwen2.5-7B-Instruct","name":"Qwen2.5 7B Instruct（本地部署/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"meta-llama/Meta-Llama-3.1-8B-Instruct","name":"Llama 3.1 8B Instruct（本地部署/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 237, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000074', 'default', 'textgen-webui', 'Text Generation WebUI（本地）', 'http://host.docker.internal:5000/v1', '{"apiKey":""}', '[{"code":"qwen2.5-7b-instruct","name":"Qwen2.5 7B Instruct（本地部署/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（本地部署/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 238, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000075', 'default', 'sglang-local', 'SGLang（本地）', 'http://host.docker.internal:30000/v1', '{"apiKey":""}', '[{"code":"Qwen/Qwen2.5-7B-Instruct","name":"Qwen2.5 7B Instruct（本地部署/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"meta-llama/Meta-Llama-3.1-8B-Instruct","name":"Llama 3.1 8B Instruct（本地部署/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 239, 60000, 60, now() - interval '1 day')
ON CONFLICT (tenant_id, provider_code) WHERE deleted = false DO UPDATE SET
    provider_name = EXCLUDED.provider_name,
    endpoint = EXCLUDED.endpoint,
    api_key_ref = EXCLUDED.api_key_ref,
    model_list_json = EXCLUDED.model_list_json,
    protocol = EXCLUDED.protocol,
    enabled = EXCLUDED.enabled,
    priority = EXCLUDED.priority,
    timeout_ms = EXCLUDED.timeout_ms,
    rate_limit_per_min = EXCLUDED.rate_limit_per_min,
    updated_time = now();

-- 2026-07 追加：第二轮扩展——国际免费额度、国内模型聚合与本地 OpenAI 兼容部署模板
INSERT INTO ai_provider (id, tenant_id, provider_code, provider_name, endpoint, api_key_ref, model_list_json, protocol, enabled, priority, timeout_ms, rate_limit_per_min, created_time)
VALUES
  -- 国际免费额度/需 Key
  ('01JYYDEMOAIPROV000076', 'default', 'deepinfra', 'DeepInfra', 'https://api.deepinfra.com/v1/openai', '{"apiKey":""}', '[{"code":"deepseek-ai/DeepSeek-V3","name":"DeepSeek-V3（免费额度/需 Key）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"meta-llama/Meta-Llama-3.1-8B-Instruct","name":"Llama 3.1 8B Instruct（免费额度/需 Key）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 240, 60000, 30, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000077', 'default', 'venice-ai', 'Venice AI', 'https://api.venice.ai/api/v1', '{"apiKey":""}', '[{"code":"venice-uncensored","name":"Venice Uncensored（免费额度/需 Key）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"venice-uncensored-mini","name":"Venice Uncensored Mini（免费额度/需 Key）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 241, 60000, 20, now() - interval '1 day'),
  -- 国内模型聚合/BYOK
  ('01JYYDEMOAIPROV000078', 'default', 'dmxapi', 'DMXAPI', 'https://www.dmxapi.com/v1', '{"apiKey":""}', '[{"code":"deepseek-chat","name":"DeepSeek-V3（按量/BYOK）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"gpt-4o-mini","name":"GPT-4o mini（按量/BYOK）","contextWindow":128000,"priceInputCny":0.0015,"priceOutputCny":0.0060}]', 'OPENAI_COMPATIBLE', false, 242, 60000, 30, now() - interval '1 day'),
  -- 本地/私有部署：完全免费，需用户自行启动服务
  ('01JYYDEMOAIPROV000079', 'default', 'koboldcpp', 'KoboldCPP（本地）', 'http://host.docker.internal:5001/v1', '{"apiKey":""}', '[{"code":"qwen2.5-7b-instruct","name":"Qwen2.5 7B Instruct（本地部署/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（本地部署/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 243, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000080', 'default', 'llamafile', 'llamafile（本地）', 'http://host.docker.internal:8080/v1', '{"apiKey":""}', '[{"code":"qwen2.5-7b-instruct","name":"Qwen2.5 7B Instruct（本地单文件/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（本地单文件/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 244, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000081', 'default', 'localai', 'LocalAI（本地）', 'http://host.docker.internal:8080/v1', '{"apiKey":""}', '[{"code":"qwen2.5-7b-instruct","name":"Qwen2.5 7B Instruct（本地部署/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（本地部署/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 245, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000082', 'default', 'fastchat', 'FastChat（本地）', 'http://host.docker.internal:8000/v1', '{"apiKey":""}', '[{"code":"qwen2.5-7b-instruct","name":"Qwen2.5 7B Instruct（本地部署/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（本地部署/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 246, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000083', 'default', 'tabbyapi', 'TabbyAPI（本地）', 'http://host.docker.internal:5000/v1', '{"apiKey":""}', '[{"code":"qwen2.5-7b-instruct","name":"Qwen2.5 7B Instruct（本地部署/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（本地部署/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 247, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000084', 'default', 'aphrodite-engine', 'Aphrodite Engine（本地）', 'http://host.docker.internal:2242/v1', '{"apiKey":""}', '[{"code":"qwen2.5-7b-instruct","name":"Qwen2.5 7B Instruct（本地部署/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（本地部署/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 248, 60000, 60, now() - interval '1 day')
ON CONFLICT (tenant_id, provider_code) WHERE deleted = false DO UPDATE SET
    provider_name = EXCLUDED.provider_name,
    endpoint = EXCLUDED.endpoint,
    api_key_ref = EXCLUDED.api_key_ref,
    model_list_json = EXCLUDED.model_list_json,
    protocol = EXCLUDED.protocol,
    enabled = EXCLUDED.enabled,
    priority = EXCLUDED.priority,
    timeout_ms = EXCLUDED.timeout_ms,
    rate_limit_per_min = EXCLUDED.rate_limit_per_min,
    updated_time = now();
