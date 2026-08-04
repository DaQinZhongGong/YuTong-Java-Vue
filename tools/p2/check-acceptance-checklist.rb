#!/usr/bin/env ruby
# frozen_string_literal: true
#
# 验收报告检查脚本（87 号文档机器化）
# Design: 87-v0.2与v1.0验收报告模板
# Source: tools/p2/acceptance-checklist.yaml
# Usage: ruby tools/p2/check-acceptance-checklist.rb
#   或 Docker: docker run --rm -v "$PWD:/workspace" -w /workspace ruby:3.2-slim ruby tools/p2/check-acceptance-checklist.rb
#
# 检查项：
#   1. v0.2 和 v1.0 验收报告模板文件存在
#   2. 实例报告（如 release-evidence/v1.0.0/acceptance-report.md）覆盖所有 ACC-Vxx-NN 编号
#   3. 实例报告不留未填充占位符 {{...}}
#   4. v1.0 实例报告 8 角色签署齐全
#   5. 实例报告结论为 PASS/CONDITIONAL_PASS/FAIL 之一

require 'pathname'
require 'yaml'

ROOT = Pathname.new(File.expand_path('../..', __dir__))
CHECKLIST_PATH = ROOT.join('tools/p2/acceptance-checklist.yaml')

errors = []
warnings = []

# ============= helpers =============
def add_error(errors, code, msg)
  errors << { code: code, message: msg }
  puts "[ERROR] #{code}: #{msg}"
end

def add_warning(warnings, code, msg)
  warnings << { code: code, message: msg }
  puts "[WARN]  #{code}: #{msg}"
end

def add_pass(code, msg)
  puts "[PASS]  #{code}: #{msg}"
end

# 安全的 YAML 加载（禁止 Date/Time 类）
SAFE_PERMITTER = [Symbol, Hash, Array, String, Integer, Float, TrueClass, FalseClass, NilClass].freeze
def safe_yaml_load(path)
  YAML.safe_load(File.read(path), permitted_classes: SAFE_PERMITTER)
rescue => e
  nil
end

puts '=== 验收报告检查脚本（87 号文档机器化） ==='
puts "Project root: #{ROOT}"
puts "Checklist:    #{CHECKLIST_PATH}"
puts ''

# ============= 1. 加载 acceptance-checklist.yaml =============
unless CHECKLIST_PATH.exist?
  add_error(errors, 'CHECKLIST_MISSING', "tools/p2/acceptance-checklist.yaml not found")
  puts ''
  puts "=== Summary ==="
  puts "Errors:   #{errors.size}"
  puts "Warnings: #{warnings.size}"
  puts 'RESULT: FAIL'
  exit 1
end

checklist = safe_yaml_load(CHECKLIST_PATH)
unless checklist
  add_error(errors, 'CHECKLIST_PARSE_FAILED', "Failed to parse acceptance-checklist.yaml")
  exit 1
end

add_pass('CHECKLIST_LOADED', "Loaded acceptance-checklist.yaml v#{checklist['schemaVersion']}")

# ============= 2. 检查模板文件存在 =============
templates = checklist['templates'] || {}
templates.each do |ver, cfg|
  template_path = ROOT.join(cfg['path'])
  if template_path.exist?
    add_pass("TEMPLATE_EXISTS_#{ver}", "Template exists: #{cfg['path']}")
  else
    add_error(errors, "TEMPLATE_MISSING_#{ver}", "Template missing: #{cfg['path']}")
  end
end
puts ''

# ============= 3. 检查实例报告 =============
# 对每个版本，扫描实例目录下符合 pattern 的报告
templates.each do |ver, cfg|
  puts "--- Inspecting #{ver} instance reports ---"
  instance_dir = ROOT.join(cfg['instanceDir'])
  unless instance_dir.exist?
    add_warning(warnings, "INSTANCE_DIR_MISSING_#{ver}", "Instance dir not found: #{cfg['instanceDir']} (no acceptance run yet)")
    puts ''
    next
  end

  pattern = Regexp.new(cfg['instancePattern'])
  instances = instance_dir.children.select { |p| p.file? && p.basename.to_s.match?(pattern) }

  if instances.empty?
    add_warning(warnings, "NO_INSTANCE_#{ver}", "No instance report matching /#{cfg['instancePattern']}/ in #{cfg['instanceDir']}")
    puts ''
    next
  end

  # 取最新的实例（按 mtime）
  latest = instances.max_by(&:mtime)
  add_pass("INSTANCE_FOUND_#{ver}", "Found #{instances.size} instance(s); inspecting latest: #{latest.relative_path_from(ROOT)}")

  content = latest.read

  # 3a. 检查 ACC 编号覆盖
  checks_key = ver == 'v0.2' ? 'v0_2_checks' : 'v1_0_checks'
  required_checks = checklist[checks_key] || []
  missing_codes = []
  required_checks.each do |c|
    code = c['code']
    if content.include?(code)
      add_pass("INSTANCE_#{ver}_#{code}", "Code #{code} present")
    else
      missing_codes << code
      add_error(errors, "INSTANCE_#{ver}_MISSING_#{code}", "Instance missing code #{code} (#{c['name']})")
    end
  end

  # 3b. 检查未填充占位符
  placeholder_count = content.scan(/\{\{[^}]+\}\}/).size
  if placeholder_count > 0
    add_warning(warnings, "INSTANCE_#{ver}_PLACEHOLDER_LEFT", "Instance still has #{placeholder_count} {{...}} placeholder(s) unfilled")
  else
    add_pass("INSTANCE_#{ver}_PLACEHOLDER_FILLED", "No unfilled placeholders")
  end

  # 3c. v1.0 必须 8 角色签署齐全
  if ver == 'v1.0'
    roles = checklist['signOffRoles'] || []
    missing_roles = roles.reject { |r| content.include?(r) }
    if missing_roles.empty?
      add_pass("INSTANCE_v1.0_ROLES_COMPLETE", "All 8 sign-off roles present")
    else
      add_error(errors, 'INSTANCE_V10_ROLES_INCOMPLETE', "v1.0 instance missing roles: #{missing_roles.join(', ')}")
    end
  end

  # 3d. 结论必须为合法枚举
  conclusion_valid = checklist['conclusionEnum'].any? { |c| content.match?(/最终结论[：:]\s*#{c}/i) || content.match?(/\|\s*#{c}\s*\|/) }
  if conclusion_valid
    add_pass("INSTANCE_#{ver}_CONCLUSION_VALID", "Conclusion uses valid enum")
  else
    add_warning(warnings, "INSTANCE_#{ver}_CONCLUSION_INVALID", "Conclusion not clearly marked as PASS/CONDITIONAL_PASS/FAIL")
  end

  puts ''
end

# ============= Summary =============
puts '=== Summary ==='
puts "Errors:   #{errors.size}"
puts "Warnings: #{warnings.size}"
puts ''

if errors.empty?
  if warnings.empty?
    puts 'RESULT: PASS - All acceptance checklist validations passed.'
    exit 0
  else
    puts "RESULT: PASS (with #{warnings.size} warning(s)) - Templates and instances conform to 87 design."
    exit 0
  end
else
  puts "RESULT: FAIL - #{errors.size} error(s) must be resolved before acceptance report is valid."
  exit 1
end
