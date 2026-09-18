# DESIGN.md — YuTong 雨桐 · 品牌设计系统总契约

> 来源：open-design · `DESIGN.md` 9 段式 + `reference-design-contract` + `brandkit`
> 作用：所有前端产出（web-admin / mobile-uniapp / 海报 / 幻灯片 / 报表）的视觉唯一真相源。
> 位置：根 `DESIGN.md` 为全局契约，`design-systems/yutong/` 为可分发包。

---

## 1. Brand Essence

YuTong 雨桐 — "雨润万物，桐生有信"。商业级技术底座，气质关键词：**可信、克制、精密、生长**。
不是酷炫的消费品，而是企业愿意把核心业务托付的底座。设计上追求"安静的专业感"：低饱和、强秩序、细腻质感。

## 2. Palette（以 `web-admin/src/styles/variables.css` 的 `--yt-*` 为唯一真相源）

| Token | Value | 用途 |
|-------|-------|------|
| `--yt-color-primary` | `#2563eb` | 主色：信任蓝，主按钮/导航/标题 |
| `--yt-color-success` | `#16a34a` | 成功/通过 |
| `--yt-color-warning` | `#d97706` | 警告 |
| `--yt-color-danger` | `#dc2626` | 危险 |
| `--yt-color-info` | `#64748b` | 信息 |
| `--yt-bg-page` | `#f6f8fb` | 页面底色 |
| `--yt-bg-card` | `#ffffff` | 卡片底色 |
| `--yt-border-default` | `#e5e7eb` | 边框/分割 |
| `--yt-text-primary` | `#111827` | 主文本 |
| `--yt-text-secondary` | `#4b5563` | 次要文本 |

> 历史别名 `--yutong-*` 指向 `--yt-*`（见 variables.css 末尾）。禁止随意引入新主色；`echarts` 配色用 `yutong-chart-palette`（见 design-systems/yutong/tokens.css）。暗色/大屏/紧凑主题见 `dark.css`。

## 3. Typography

- **中文**：`Noto Sans SC` / 系统苹方，标题 600，正文 400
- **英文/数字**：`Inter` / `JetBrains Mono`（代码/ID）
- 层级：`H1 28/36 600` / `H2 20/28 600` / `H3 16/24 600` / `Body 14/22 400` / `Caption 12/16 400`
- 禁止超过 3 种字重同屏。

## 4. Layout & Grid

- 12 列栅格，`gap 24`，卡片 `padding 20-24`，页面 `max-width 1440` 居中
- 管理端：左侧 220 导航 + 顶部 56 面包屑 + 内容区 `24` 内边距
- 信息密度：**中高密度但强留白** — 用分割线与卡片而非堆色块

## 5. Components

- 按钮：主按钮深海蓝、次按钮白底描边、文本按钮无边框；圆角 8，高度 32/40
- 表格：表头 `bg #F1F5F9`、`font 13 600`、`color muted`；行高 48，hover `#F8FAFC`
- 表单：`label 13 muted` 上置，`input 32h`，错误态红字+红框
- 空状态：插画 + 一句话 + 主操作按钮（用 `PageState.vue`）
- 徽标：`StatusBadge/StatusTag` 统一状态色（成功松绿/待审琥珀/失败红）

## 6. Motion

- 时长 `150-220ms`，`ease-out`，禁止弹跳
- 仅对 `opacity/transform` 做过渡，列表用 `stagger 30ms`

## 7. Imagery & Iconography

- 图标：`@element-plus/icons-vue` 线性图标，`16-20px`，描边 1.5
- 插画：线性淡彩，避免拟物与过度渐变
- 数据图：`echarts` 冷静配色，禁止彩虹色

## 8. Voice & Copy

- 文案克制、动词开头："创建请求" 而非 "点击这里创建请求"
- 错误提示给原因+出路："无权访问该租户数据，请联系管理员分配 DataScope"

## 9. Anti AI-Slop Checklist（出图前必检）

- [ ] 没有紫色渐变+毛玻璃滥用？
- [ ] 没有圆角过大+过度阴影？
- [ ] 没有相同卡片无限重复无层次？
- [ ] 主色不超过 1 个+1 个强调色？
- [ ] 字重/字号层级清晰？
- [ ] 空状态/加载/错误态都已设计？

---

## 使用方法

```ts
// web-admin/src/styles/tokens.css 已引入
import '@/styles/tokens.css'
// 然后直接用 var(--yutong-*) token
```

新页面/组件必须先读本文件，再用 `frontend-design` / `canvas-design` skill 生成初稿，最后用 `plan-design-review` 自检。
