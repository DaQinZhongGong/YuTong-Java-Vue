$ErrorActionPreference = 'Stop'
$path = 'd:\MyCode\YuTong-Java-Vue\database\seed\R__seed_demo_data.sql'

# Read raw bytes
$bytes = [System.IO.File]::ReadAllBytes($path)

# Detect BOM
$hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
Write-Host "Has UTF-8 BOM: $hasBom"

# Try UTF-8 first
$utf8 = New-Object System.Text.UTF8Encoding($false)
$utf8Text = $utf8.GetString($bytes)
$gbk = [System.Text.Encoding]::GetEncoding(936)
$gbkText = $gbk.GetString($bytes)

# Check which encoding has the anchor
$anchor = '-- ===== AI Prompt'
$utfIdx = $utf8Text.IndexOf($anchor)
$gbkIdx = $gbkText.IndexOf($anchor)
Write-Host "UTF8 anchor idx: $utfIdx"
Write-Host "GBK anchor idx: $gbkIdx"

# Use whichever encoding found the anchor; prefer UTF-8 if both work
if ($utfIdx -ge 0) {
    $text = $utf8Text
    $enc = $utf8
    Write-Host "Using UTF-8 encoding"
} elseif ($gbkIdx -ge 0) {
    $text = $gbkText
    $enc = $gbk
    Write-Host "Using GBK encoding"
} else {
    Write-Host "ERROR: Anchor not found in either encoding!"
    exit 1
}

# Detect line ending
$crlfCount = ([regex]::Matches($text, "`r`n")).Count
$lfOnlyCount = ([regex]::Matches($text, "(?<!`r)`n")).Count
Write-Host "CRLF count: $crlfCount"
Write-Host "LF-only count: $lfOnlyCount"
$lineEnding = if ($crlfCount -gt $lfOnlyCount) { "`r`n" } else { "`n" }
Write-Host "Using line ending: $(if ($lineEnding -eq "`r`n") {'CRLF'} else {'LF'})"

# Split into lines, preserving line endings
$lines = $text -split "`r?`n"
Write-Host "Total lines: $($lines.Count)"

# Find the target: the line "ON CONFLICT DO NOTHING;" that is immediately followed by
# an empty line and then "-- ===== AI Prompt 模板 ====="
$insertAfterIdx = -1
for ($i = 0; $i -lt $lines.Count - 2; $i++) {
    if ($lines[$i].Trim() -eq 'ON CONFLICT DO NOTHING;' -and $lines[$i+2].Contains('AI Prompt')) {
        $insertAfterIdx = $i
        Write-Host "Found insertion point at line $($i+1) (0-based: $i)"
        Write-Host "  Line $i: [$($lines[$i])]"
        Write-Host "  Line $($i+1): [$($lines[$i+1])]"
        Write-Host "  Line $($i+2): [$($lines[$i+2])]"
        break
    }
}

if ($insertAfterIdx -lt 0) {
    Write-Host "ERROR: Could not find insertion point!"
    exit 1
}

# Build the new SQL block
$newLines = @(
    '',
    '-- ===== AI 模型供应商（扩展：免费/试用补充） =====',
    '-- 说明：补充 10 家免费/试用 LLM 供应商。pollinations 与本地 ollama 无需 key 默认启用，其余需管理员填入真实 API Key 后启用。',
    'INSERT INTO ai_provider (id, tenant_id, provider_code, provider_name, endpoint, api_key_ref, model_list_json, protocol, enabled, priority, timeout_ms, rate_limit_per_min, created_time)',
    'VALUES',
    "  ('01JYYDEMOAIPROV000021', 'default', 'pollinations',     'Pollinations AI',        'https://text.pollinations.ai/openai',   '{""apiKey"":""""}',       '[{""code"":""openai"",""name"":""OpenAI Compatible"",""contextWindow"":8192,""priceInputCny"":0.0000,""priceOutputCny"":0.0000},{""code"":""mistral"",""name"":""Mistral"",""contextWindow"":8192,""priceInputCny"":0.0000,""priceOutputCny"":0.0000}]', 'OPENAI_COMPATIBLE', true,  5,   60000, 60, now() - interval '1 day'),",
    "  ('01JYYDEMOAIPROV000022', 'default', 'ollama',           'Ollama 本地推理',        'http://localhost:11434/v1',             '{""apiKey"":""ollama""}', '[{""code"":""llama3.1"",""name"":""Llama 3.1 8B"",""contextWindow"":128000,""priceInputCny"":0.0000,""priceOutputCny"":0.0000},{""code"":""qwen2.5"",""name"":""Qwen 2.5 7B"",""contextWindow"":32768,""priceInputCny"":0.0000,""priceOutputCny"":0.0000}]', 'OPENAI_COMPATIBLE', true,  10,  60000, 60, now() - interval '1 day'),",
    "  ('01JYYDEMOAIPROV000023', 'default', 'groq',             'Groq',                   'https://api.groq.com/openai/v1',        '{""apiKey"":""""}',       '[{""code"":""llama-3.3-70b-versatile"",""name"":""Llama 3.3 70B Versatile"",""contextWindow"":128000,""priceInputCny"":0.0000,""priceOutputCny"":0.0000},{""code"":""llama-3.1-8b-instant"",""name"":""Llama 3.1 8B Instant"",""contextWindow"":128000,""priceInputCny"":0.0000,""priceOutputCny"":0.0000}]', 'OPENAI_COMPATIBLE', false, 25,  60000, 30, now() - interval '1 day'),",
    "  ('01JYYDEMOAIPROV000024', 'default', 'together-ai',      'Together AI',            'https://api.together.xyz/v1',           '{""apiKey"":""""}',       '[{""code"":""meta-llama/Llama-3.3-70B-Instruct-Turbo-Free"",""name"":""Llama 3.3 70B Free"",""contextWindow"":128000,""priceInputCny"":0.0000,""priceOutputCny"":0.0000},{""code"":""meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo"",""name"":""Llama 3.1 8B Turbo"",""contextWindow"":128000,""priceInputCny"":0.0001,""priceOutputCny"":0.0001}]', 'OPENAI_COMPATIBLE', false, 35,  60000, 20, now() - interval '1 day'),",
    "  ('01JYYDEMOAIPROV000025', 'default', 'openrouter',       'OpenRouter',             'https://openrouter.ai/api/v1',          '{""apiKey"":""""}',       '[{""code"":""meta-llama/llama-3.1-8b-instruct:free"",""name"":""Llama 3.1 8B Free"",""contextWindow"":128000,""priceInputCny"":0.0000,""priceOutputCny"":0.0000},{""code"":""google/gemini-flash-1.5:free"",""name"":""Gemini Flash 1.5 Free"",""contextWindow"":1048576,""priceInputCny"":0.0000,""priceOutputCny"":0.0000}]', 'OPENAI_COMPATIBLE', false, 40,  60000, 20, now() - interval '1 day'),",
    "  ('01JYYDEMOAIPROV000026', 'default', 'cerebras',         'Cerebras',               'https://api.cerebras.ai/v1',            '{""apiKey"":""""}',       '[{""code"":""llama3.1-8b"",""name"":""Llama 3.1 8B"",""contextWindow"":128000,""priceInputCny"":0.0000,""priceOutputCny"":0.0000},{""code"":""llama-3.3-70b"",""name"":""Llama 3.3 70B"",""contextWindow"":128000,""priceInputCny"":0.0000,""priceOutputCny"":0.0000}]', 'OPENAI_COMPATIBLE', false, 45,  60000, 30, now() - interval '1 day'),",
    "  ('01JYYDEMOAIPROV000027', 'default', 'sambanova',        'SambaNova',              'https://api.sambanova.ai/v1',           '{""apiKey"":""""}',       '[{""code"":""Meta-Llama-3.1-8B-Instruct"",""name"":""Llama 3.1 8B"",""contextWindow"":128000,""priceInputCny"":0.0000,""priceOutputCny"":0.0000},{""code"":""Meta-Llama-3.1-70B-Instruct"",""name"":""Llama 3.1 70B"",""contextWindow"":128000,""priceInputCny"":0.0000,""priceOutputCny"":0.0000}]', 'OPENAI_COMPATIBLE', false, 50,  60000, 20, now() - interval '1 day'),",
    "  ('01JYYDEMOAIPROV000028', 'default', 'silicon-flow',     '硅基流动',               'https://api.siliconflow.cn/v1',         '{""apiKey"":""""}',       '[{""code"":""Qwen/Qwen2.5-7B-Instruct"",""name"":""Qwen 2.5 7B（免费）"",""contextWindow"":32768,""priceInputCny"":0.0000,""priceOutputCny"":0.0000},{""code"":""deepseek-ai/DeepSeek-V3"",""name"":""DeepSeek V3"",""contextWindow"":64000,""priceInputCny"":0.0010,""priceOutputCny"":0.0010}]', 'OPENAI_COMPATIBLE', false, 55,  60000, 60, now() - interval '1 day'),",
    "  ('01JYYDEMOAIPROV000029', 'default', 'deepseek',         'DeepSeek',               'https://api.deepseek.com/v1',           '{""apiKey"":""""}',       '[{""code"":""deepseek-chat"",""name"":""DeepSeek Chat"",""contextWindow"":64000,""priceInputCny"":0.0010,""priceOutputCny"":0.0010},{""code"":""deepseek-reasoner"",""name"":""DeepSeek Reasoner"",""contextWindow"":64000,""priceInputCny"":0.0020,""priceOutputCny"":0.0080}]', 'OPENAI_COMPATIBLE', false, 60,  60000, 60, now() - interval '1 day'),",
    "  ('01JYYDEMOAIPROV000030', 'default', 'minimax',          'MiniMax',                'https://api.minimax.chat/v1',           '{""apiKey"":""""}',       '[{""code"":""abab6.5s-chat"",""name"":""ABAB 6.5s Chat"",""contextWindow"":245760,""priceInputCny"":0.0005,""priceOutputCny"":0.0005},{""code"":""abab6.5t-chat"",""name"":""ABAB 6.5t Chat"",""contextWindow"":8192,""priceInputCny"":0.0020,""priceOutputCny"":0.0020}]', 'OPENAI_COMPATIBLE', false, 65,  60000, 60, now() - interval '1 day')",
    'ON CONFLICT DO NOTHING;'
)

# Insert the new lines after the found index (which is the ON CONFLICT line)
# The structure is: [insertAfterIdx] = "ON CONFLICT DO NOTHING;", [insertAfterIdx+1] = "", [insertAfterIdx+2] = "-- ===== AI Prompt..."
# We want to insert our new block between [insertAfterIdx] and [insertAfterIdx+1]
$resultLines = New-Object System.Collections.ArrayList
for ($i = 0; $i -le $insertAfterIdx; $i++) {
    [void]$resultLines.Add($lines[$i])
}
foreach ($nl in $newLines) {
    [void]$resultLines.Add($nl)
}
for ($i = $insertAfterIdx + 1; $i -lt $lines.Count; $i++) {
    [void]$resultLines.Add($lines[$i])
}

Write-Host "Original lines: $($lines.Count)"
Write-Host "New lines: $($resultLines.Count)"
Write-Host "Inserted $($newLines.Count) new lines"

# Join with the detected line ending
$resultText = [string]::Join($lineEnding, $resultLines.ToArray())

# Write back with the same encoding
[System.IO.File]::WriteAllText($path, $resultText, $enc)
Write-Host "File written successfully with $(if($enc.EncodingName -match 'UTF8'){'UTF-8'}else{'GBK'}) encoding"

# Verify: re-read and check
$verifyText = [System.IO.File]::ReadAllText($path, $enc)
$verifyPollinations = $verifyText.Contains("pollinations")
$verifyMinimax = $verifyText.Contains("minimax")
$verifyPrompt = $verifyText.Contains("-- ===== AI Prompt")
$verifyBackslash = $verifyText.Contains('\\"')
Write-Host "Verification - pollinations found: $verifyPollinations"
Write-Host "Verification - minimax found: $verifyMinimax"
Write-Host "Verification - AI Prompt anchor found: $verifyPrompt"
Write-Host "Verification - backslash-quote found (should be False): $verifyBackslash"

# Count provider records
$provCount = ([regex]::Matches($verifyText, "01JYYDEMOAIPROV0000\d{2}")).Count
Write-Host "Total provider ID matches: $provCount"
