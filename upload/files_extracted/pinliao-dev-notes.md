# 聘聊（招聘App）开发笔记

> 用途：保留开发现场。下次对话开始时，把本文件和最新的 `pinliao-app.html`（或 artifact 链接）发给 Claude，即可快速接续。
> 最后更新：2026-09-21，前端原型 v0.2

## 1. 项目概述与约束

- 目标：做一款参考 Boss 直聘的招聘 App，最终前端到后端都做。
- 当前阶段：因电脑硬件限制，先只做前端基础页面和简单交互，后续按对话需求陆续完善。
- App 暂名「聘聊」（占位名，可改）。视觉上参考 Boss 的青绿色调，但配色、文案、头像均为自有设计，不使用其品牌资产。

## 2. 制品位置

| 内容 | 位置 |
|---|---|
| 源码 | `pinliao-app.html`（单文件，HTML+CSS+JS，无外部依赖） |
| 在线预览 | https://claude.ai/artifact/V4qF8dLrFP5vGiteHbyG4G |
| 续开发方式 | 用 Artifact 的 read 取回源码 → 修改 → 用同一链接重新发布 |

## 3. 需求与决策记录（问答摘要）

1. **起点**：用户希望 Claude 以资深架构师身份参与设计与开发招聘 App，参考 Boss 直聘；因硬件限制先做前端几个基础页面 + 简单交互。
   → 交付 v0.1：职位列表/搜索/筛选、职位详情、消息与聊天（模拟招聘者自动回复）、我的（求职期望、收藏、简历预览、深浅色）。
2. **岗位分类**：用户指出仅限计算机类太单薄，岗位应可选择，或提供市面常见分类。
   → 交付 v0.2：17 个一级分类 + 二级岗位；职位页分类标签联动；求职期望可下拉选择；推荐排序按期望岗位优先；职位扩充到 51 个；城市新增武汉、苏州。
3. **保留现场**：用户要求把代码和问答信息保存进项目，方便后续继续开发。→ 即本文件。

**尚未确认的事项**（等待用户决定）：
- 下一步做什么：完善求职者端（投递记录、面试邀请、简历编辑、公司主页），还是先做招聘者端（发布职位、牛人列表）。
- 目标平台：原生 App / 小程序 / H5。
- 技术栈偏好（前端框架、后端语言）。

## 4. 功能清单（v0.2）

- 职位 Tab：城市切换、实时搜索（职位/公司/分类/岗位/技能）、一级分类标签 + 二级岗位标签、筛选面板（排序/经验/薪资）、空状态。
- 职位详情页：薪资、福利、招聘者卡片、职责与要求、公司信息、收藏、立即沟通/继续沟通。
- 消息 Tab：会话列表、未读角标、预置两条会话。
- 聊天页：消息气泡、快捷回复（发送简历/问薪资/约面试）、关键词自动回复、"对方正在输入"。
- 我的 Tab：个人卡片、求职期望编辑（类别→岗位联动）、收藏列表、简历预览、外观切换（跟随系统/浅色/深色）、招聘者身份切换（仅占位提示）。

## 5. 代码结构速查（pinliao-app.html）

`<script>` 内按区块组织：

| 区块 | 内容 |
|---|---|
| 数据 | `CO` 公司字典、`J()` 职位构造器、`JOBS` 职位数组、`TREE` 分类树（一级→二级）、`RESP` 各一级分类的职责文案、`CITIES` |
| 状态 | 全局状态 `S`（tab/city/q/cat/sub/sort/exp/sal/favs/chats/theme/profile）；`save()/load()`，localStorage 键 `pinliao.v2` |
| Tab 与列表 | `switchTab`、`renderJobs`、`renderList`、`filtered()`（筛选 + 推荐排序）、`jobRow` |
| 消息 | `renderMsgs`、`convRow` |
| 我的 | `renderMe` |
| 页面栈 | `pushPage/popPage/refreshTop`，页面类型：job / chat / favs / resume |
| 聊天 | `startChat/openChat/addMsg/hrReply/sendFrom/refreshChat`，会话以 `jobId` 为键 |
| 弹层 | `openSheet/closeSheet`，城市、筛选、求职期望三个 sheet |
| 事件 | 全局事件委托：元素写 `data-a="动作"`，由 `H` 事件表分发；`input/change/keydown` 单独监听 |

**职位数据字段**：`id, title, sMin, sMax, salary, city, area, exp, edu, cat(一级), sub(二级), tags[], company, stage, size, industry, hr{name,title}, days`

**设计约定**：CSS 变量做主题（`--accent` 青绿、`--sal` 薪资红），深色通过 `prefers-color-scheme` 与 `data-theme` 两路定义；移动端优先，桌面居中 430px；无外部脚本、字体与图片；输入框字号 16px 防 iOS 缩放。

## 6. 已知限制

- 数据、聊天、自动回复均为前端模拟，无真实账号与接口；localStorage 仅存于当前浏览器。
- 会话以 `jobId` 为键，接后端时应改为独立的会话 ID。
- 简历为静态预览，不可编辑；无投递流程与面试邀请；无分页/无限滚动。
- 未处理安卓物理返回键 / 浏览器后退。
- 单文件结构便于原型迭代，不适合长期维护。

## 7. 后续路线（建议，未经用户确认）

1. 补齐求职者端：简历编辑、投递记录、面试邀请、公司主页。
2. 补招聘者端：发布职位、牛人列表、沟通管理。
3. 先出 API 设计（前后端并行的前提），再把原型迁移为工程化项目。
4. 技术栈方向（待用户确认）：前端 Vue 3 + Vite + TypeScript（移动端 UI 可选 Vant），跨端可考虑 uni-app；后端 Spring Boot 或 NestJS + PostgreSQL + Redis，聊天用 WebSocket。

**核心实体草案**：User、Resume、Company、Job、Category（分类树）、Conversation、Message、Application（投递）、Favorite、Interview。

## 8. 接续开发的最快方式

1. 新对话开头发送：本文件 + artifact 链接（或 `pinliao-app.html`）。
2. 说明本次想做的事，例如"继续做投递流程"或"开始设计后端 API"。
3. 若要补充信息，可提前告诉 Claude：目标平台、技术栈偏好、是否要做招聘者端、是学习练手还是计划上线。
