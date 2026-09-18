$ErrorActionPreference = 'Stop'
$root = 'D:\MyCode\YuTong-Java-Vue'
$pass=0; $fail=0; $warn=0
$results = New-Object System.Collections.ArrayList
function Add($id,$status,$msg){
  [void]$results.Add([PSCustomObject]@{ID=$id;Status=$status;Detail=$msg})
  if($status -eq 'PASS'){ $script:pass++ } elseif($status -eq 'FAIL'){ $script:fail++ } else { $script:warn++ }
}
function Exists($path,$id,$name){
  if(Test-Path $path){ Add $id 'PASS' "$name OK" } else { Add $id 'FAIL' "$name MISSING: $path" }
}
Exists "$root\AGENTS.md" "S01" "AGENTS.md"
Exists "$root\.agents\docs\CONTEXT.md" "S02" "CONTEXT.md"
Exists "$root\DESIGN.md" "S03" "DESIGN.md"
Exists "$root\design-systems\yutong\tokens.css" "S04" "yutong tokens.css"
Exists "$root\design-systems\yutong\manifest.json" "S05" "yutong manifest.json"
Exists "$root\.agents\skills\README.md" "S06" "skills README"
Exists "$root\.agents\adr\0001-skills-adoption.md" "S07" "ADR 0001"
Exists "$root\.agents\docs\EVOLUTION.md" "S08" "EVOLUTION.md"
Exists "$root\.agents\skills\yutong-checks\SKILL.md" "S09" "yutong-checks skill"
Exists "$root\.agents\skills\yutong-design-tokens\SKILL.md" "S10" "yutong-design-tokens skill"
$content = Get-Content "$root\AGENTS.md" -Raw -Encoding UTF8
if($content -match 'grill-with-docs'){ Add "S11" "PASS" "AGENTS contains grill-with-docs" } else { Add "S11" "FAIL" "AGENTS missing grill-with-docs" }
if($content -match 'DESIGN'){ Add "S12" "PASS" "AGENTS contains DESIGN" } else { Add "S12" "WARN" "AGENTS missing DESIGN" }
$tokens = Get-Content "$root\design-systems\yutong\tokens.css" -Raw -Encoding UTF8
if($tokens -match 'yt-color-primary'){ Add "S13" "PASS" "tokens reuses yt-color-primary" } else { Add "S13" "FAIL" "tokens not reusing yt-*" }
Write-Host "`n=== YuTong Skills Architecture Check ==="
$results | Format-Table ID,Status,Detail -AutoSize | Out-String | Write-Host
Write-Host "PASS=$pass WARN=$warn FAIL=$fail"
if($fail -gt 0){ exit 1 } else { Write-Host "PASSED" ; exit 0 }
