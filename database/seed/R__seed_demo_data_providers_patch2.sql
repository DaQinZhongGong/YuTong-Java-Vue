-- ============================================================
-- 新增免费 / 免费额度 LLM API 供应商补丁（第三批 / patch2）
-- 新增：18 家（id 01JYYDEMOAIPROV000085 ~ 01JYYDEMOAIPROV000102，priority 249 ~ 266）
-- 生成日期：2026-07-28
-- 来源：市场调研 + 官方/社区公开文档逐家核实 base endpoint 与免费模型 code
-- 验证状态：所有 endpoint 与 model code 均来自官方文档或权威 API 目录（apis.io / apivault 等）；
--           全部 enabled=false（模板性质），需管理员补 Key 或完成本地部署后手动启用。
-- 幂等：沿用 INSERT ... ON CONFLICT (tenant_id, provider_code) WHERE deleted=false DO UPDATE SET 写法，可重复执行。
--
-- 候选清单中经核实后【未收录】的供应商（说明原因，避免给出不可用的 OpenAI 兼容端点）：
--   - anthropic        : Anthropic 官方仅提供 /v1/messages（Anthropic 原生协议），无官方 OpenAI 兼容 /v1/chat/completions；
--                        若未来系统支持 ANTHROPIC 协议再单独补充。
--   - unify-ai         : unify.ai 官方 API 为 /v0/messages（助手会话 API），并非 OpenAI Chat Completions 兼容形态，端点不可靠，故不收。
--
-- 本地类供应商 endpoint 统一使用 host.docker.internal（与 patch1 中 lm-studio/jan 等本地模板保持一致），
-- 因为后端运行在 docker 内，localhost 指向容器自身无法到达宿主机上的本地推理服务。
-- ============================================================

INSERT INTO ai_provider (id, tenant_id, provider_code, provider_name, endpoint, api_key_ref, model_list_json, protocol, enabled, priority, timeout_ms, rate_limit_per_min, created_time)
VALUES
  -- ===== 国际/开源 免费额度 / 需 Key API =====
  ('01JYYDEMOAIPROV000085', 'default', 'akash-chat', 'Akash Chat / AkashML', 'https://chatapi.akash.network/api/v1', '{"apiKey":""}', '[{"code":"meta-llama/Llama-3.3-70B","name":"Llama 3.3 70B（Akash 免费额度/需 Key）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"deepseek-ai/DeepSeek-V3","name":"DeepSeek-V3（Akash 免费额度/需 Key）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 249, 60000, 30, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000086', 'default', 'arli-ai', 'ArliAI', 'https://api.arliai.com/v1', '{"apiKey":""}', '[{"code":"Llama-3.3-70B-ArliAI-RPMax-v3","name":"Llama 3.3 70B ArliAI RPMax v3（免费额度/需 Key）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"qwq-32b-arliai-rpr-v1","name":"QwQ 32B ArliAI RpR v1（免费额度/需 Key）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 250, 60000, 20, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000087', 'default', 'targon', 'Targon（Bittensor Subnet 4）', 'https://api.targon.com/v1', '{"apiKey":""}', '[{"code":"zai-org/GLM-4.5-Air","name":"GLM-4.5-Air（Targon 免费/需 Key）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"deepseek-ai/DeepSeek-R1-0528","name":"DeepSeek-R1（Targon 免费/需 Key）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"moonshotai/Kimi-K2-Instruct","name":"Kimi-K2-Instruct（Targon 免费/需 Key）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 251, 60000, 20, now() - interval '1 day'),

  -- ===== 国内模型 API（免费额度 / 需 Key）=====
  ('01JYYDEMOAIPROV000088', 'default', 'ai-360', '360 智脑', 'https://api.360.cn/v1', '{"apiKey":""}', '[{"code":"360gpt-flash","name":"360GPT-Flash（免费额度/需 Key）","contextWindow":32000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"360gpt-turbo","name":"360GPT-Turbo（免费额度/需 Key）","contextWindow":32000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 252, 60000, 30, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000089', 'default', 'z-ai', 'Z.AI（智谱国际版）', 'https://api.z.ai/v1', '{"apiKey":""}', '[{"code":"glm-4.5-flash","name":"GLM-4.5-Flash（100% 免费）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"glm-4.7-flash","name":"GLM-4.7-Flash（100% 免费）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 253, 60000, 30, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000090', 'default', 'moonshot-intl', 'Kimi（Moonshot 国际版）', 'https://api.moonshot.ai/v1', '{"apiKey":""}', '[{"code":"kimi-k3","name":"Kimi K3（国际版免费额度/需 Key）","contextWindow":1000000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"kimi-k2.6","name":"Kimi K2.6（国际版免费额度/需 Key）","contextWindow":256000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 254, 60000, 30, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000091', 'default', 'minimax-intl', 'MiniMax（国际版）', 'https://api.minimaxi.com/v1', '{"apiKey":""}', '[{"code":"MiniMax-M3","name":"MiniMax-M3（国际版免费额度/需 Key）","contextWindow":1000000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"MiniMax-M2.7","name":"MiniMax-M2.7（国际版免费额度/需 Key）","contextWindow":204800,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 255, 60000, 30, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000092', 'default', 'ucloud-modelverse', 'UCloud UModelVerse', 'https://api.modelverse.cn/v1', '{"apiKey":""}', '[{"code":"deepseek-ai/DeepSeek-R1","name":"DeepSeek-R1（UModelVerse 新用户 50 万免费 Tokens）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"zai-org/GLM-4.5","name":"GLM-4.5（UModelVerse 新用户 50 万免费 Tokens）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 256, 60000, 30, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000093', 'default', 'mistral-codestral', 'Mistral Codestral（代码模型）', 'https://codestral.mistral.ai/v1', '{"apiKey":""}', '[{"code":"codestral-latest","name":"Codestral Latest（Mistral 免费额度，约 1 RPS）","contextWindow":256000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 257, 60000, 20, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000094', 'default', 'qiniu-ai', '七牛云 AI 推理', 'https://api.qnaigc.com/v1', '{"apiKey":""}', '[{"code":"deepseek/deepseek-v3","name":"DeepSeek-V3（七牛云免费模型/需 Key）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"qwen/qwen3.5-35b-a3b-free","name":"Qwen3.5-35B-A3B（七牛云免费模型）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 258, 60000, 30, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000095', 'default', 'teleai-xingchen', '中国电信星辰 MaaS（TokenHub）', 'https://wishub-x6.ctyun.cn/v1', '{"apiKey":""}', '[{"code":"GLM-5","name":"GLM-5（星辰 TokenHub 免费试用额度/需 Key）","contextWindow":204800,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"Qwen3.5-397B-A17B","name":"Qwen3.5-397B-A17B（星辰 TokenHub 免费试用额度/需 Key）","contextWindow":262144,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 259, 60000, 20, now() - interval '1 day'),

  -- ===== 模板类：占位 / 本地部署（全部需手动填 Key 或启动本地服务后启用）=====
  -- Azure OpenAI：endpoint 为占位符，部署时替换为 https://<your-resource>.openai.azure.com 并将 model 改为真实 deployment 名
  ('01JYYDEMOAIPROV000096', 'default', 'azure-openai', 'Azure OpenAI（模板）', 'https://YOUR_RESOURCE.openai.azure.com', '{"apiKey":""}', '[{"code":"gpt-4o-mini","name":"gpt-4o-mini（替换为本账号 Deployment 名）","contextWindow":128000,"priceInputCny":0.0015,"priceOutputCny":0.0060}]', 'OPENAI_COMPATIBLE', false, 260, 60000, 20, now() - interval '1 day'),

  -- ===== 本地 / 私有部署：完全免费，需用户自行启动服务（endpoint 指向宿主机的 host.docker.internal）=====
  ('01JYYDEMOAIPROV000097', 'default', 'gpt4all-local', 'GPT4All 本地推理', 'http://host.docker.internal:4891/v1', '{"apiKey":""}', '[{"code":"llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（GPT4All 本地/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"qwen2.5-7b-instruct","name":"Qwen2.5 7B Instruct（GPT4All 本地/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 261, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000098', 'default', 'mlx-lm-local', 'MLX-LM 本地推理（Apple Silicon）', 'http://host.docker.internal:8080/v1', '{"apiKey":""}', '[{"code":"mlx-community/Llama-3.1-8B-Instruct-4bit","name":"Llama 3.1 8B Instruct 4bit（MLX-LM 本地/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"mlx-community/Qwen2.5-7B-Instruct-4bit","name":"Qwen2.5 7B Instruct 4bit（MLX-LM 本地/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 262, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000099', 'default', 'litellm-proxy-local', 'LiteLLM Proxy（本地聚合网关）', 'http://host.docker.internal:4000/v1', '{"apiKey":""}', '[{"code":"gpt-4o-mini","name":"GPT-4o mini（经 LiteLLM Proxy 路由/按后端额度）","contextWindow":128000,"priceInputCny":0.0015,"priceOutputCny":0.0060},{"code":"claude-3-5-haiku","name":"Claude 3.5 Haiku（经 LiteLLM Proxy 路由/按后端额度）","contextWindow":200000,"priceInputCny":0.0010,"priceOutputCny":0.0050}]', 'OPENAI_COMPATIBLE', false, 263, 60000, 60, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000100', 'default', 'cortex-local', 'Jan Cortex 本地推理引擎', 'http://host.docker.internal:1337/v1', '{"apiKey":""}', '[{"code":"llama3.1","name":"Llama 3.1（Cortex 本地/免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"qwen2.5-7b","name":"Qwen2.5 7B（Cortex 本地/免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 264, 60000, 60, now() - interval '1 day'),

  -- ===== 调研中发现的其它可靠免费供应商（公益/社区代理类如 zukijourney 等 ToS 存疑的一律未收）=====
  ('01JYYDEMOAIPROV000101', 'default', 'featherless-ai', 'Featherless AI（开源模型 Serverless）', 'https://api.featherless.ai/v1', '{"apiKey":""}', '[{"code":"meta-llama/Meta-Llama-3.1-8B-Instruct","name":"Llama 3.1 8B Instruct（Featherless 免费层/需 Key）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"Qwen/Qwen2.5-7B-Instruct","name":"Qwen2.5 7B Instruct（Featherless 免费层/需 Key）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 265, 60000, 20, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000102', 'default', 'glhf-chat', 'glhf.chat（免费开源模型推理）', 'https://glhf.chat/api/openai/v1', '{"apiKey":""}', '[{"code":"hf:meta-llama/Llama-3.1-70B-Instruct","name":"Llama 3.1 70B Instruct（glhf.chat 免费无限额度）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"hf:mistralai/Mixtral-8x7B-Instruct-v0.1","name":"Mixtral 8x7B Instruct（glhf.chat 免费无限额度）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 266, 60000, 20, now() - interval '1 day')
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
