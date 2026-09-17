# FindWork 项目现状与已完成工作

更新时间：2026-09-18
当前分支：`codex/increase-demo-jobs`
当前 PR：[PR #1](https://github.com/SHJ-SHJ0128/FindWork/pull/1)，最新提交 `336206e`

## 1. 项目目标

FindWork 是一个本地运行的个人求职岗位收集与审阅工具，目标是：

- 收集中国和新加坡的软件工程相关岗位。
- 保留较高召回率，让用户先审阅数量，再判断质量。
- 将不同来源的职位转换为统一数据结构。
- 保存原始职位链接，方便人工打开和申请。
- 对不确定字段进行标记，不伪造匹配分数或职位信息。

当前候选人方向包括 Backend、AI Application、Solutions Engineering 及相关软件工程岗位；允许城市为重庆、成都、广州、深圳、杭州；新加坡保留为支持地区；远程岗位单独标记。

## 2. 已完成的功能

### 2.1 项目基础设施

- 前端：Vue 3 + TypeScript + Vite。
- 后端：Spring Boot 3.5.6、Java 21 编译目标。
- 数据库：PostgreSQL，Docker Compose 启动。
- 数据库迁移：Flyway。
- 服务默认绑定 `127.0.0.1`，前端 CORS 仅允许 `http://127.0.0.1:5173`。

### 2.2 候选人资料

前端“候选人资料”页面支持编辑并保存：

- 目标职位族。
- 允许城市。
- 目标地区。
- 技能。
- 经验层级。
- 是否纳入远程岗位。

接口：

- `GET /api/candidate-profile`
- `PUT /api/candidate-profile`

### 2.3 岗位持久化

`job_posting` 表保存统一岗位记录，并使用 `(source, source_job_id)` 做幂等 upsert。

当前统一字段包括：

```text
id
title
company
country
city
remoteType
employmentType
experienceLevel
source
canonicalUrl
description       # 完整 JD 或原始文本
summary           # 卡片摘要
skills
salaryMin
salaryMax
salaryCurrency
salaryPeriod
salaryText
salarySource
score
needsReview
postedAt
```

数据库迁移：

- `V1__candidate_profile.sql`：候选人资料。
- `V2__job_posting.sql`：岗位表、索引和首批演示岗位。
- `V3__more_demo_jobs.sql`：补充演示岗位，总计 15 条 Demo 记录。
- `V4__job_summary_and_salary.sql`：新增 nullable `summary` 和薪资字段，不删除旧数据。

### 2.4 Greenhouse 导入

已实现公开 Greenhouse Job Board 导入：

- 支持岗位板 slug，例如 `airtable`。
- 支持完整岗位板 URL。
- 通过 Greenhouse 公共 API 获取职位和 HTML 内容。
- 保存 `absolute_url` 作为原始职位链接。
- 完整 JD 保存到 `description`，列表不直接展示完整 JD。
- 生成简短 `summary`，旧数据缺少摘要时前端自动回退到通用摘要。
- 识别标题、公司、城市、国家、办公方式、职位级别、雇佣类型和技能。
- 优先读取结构化薪资字段；没有结构化字段时解析 JD 文本。
- 重复导入使用 source + source job ID 更新，不重复计数。

接口：

```text
POST /api/providers/greenhouse/import
请求体：{"board":"airtable"}
```

已用 Airtable 岗位板完成一次幂等验证，导入 16 条记录。

### 2.5 LinkedIn 邮件导入

LinkedIn 没有面向普通求职者的公开岗位搜索 API，因此当前通过 Gmail 只读 OAuth 读取 LinkedIn 岗位提醒邮件：

- Gmail 权限为 `gmail.readonly`。
- 默认查询：`from:(linkedin.com) newer_than:7d`。
- 每封邮件按职位链接拆分岗位。
- 保留标准化 LinkedIn 原始职位 URL。
- 解析标题、公司、国家、城市、办公方式、经验层级和技能。
- 同一 LinkedIn 职位 ID 去重。
- 导入岗位默认 `needsReview=true`。
- Gmail 同步调度为每天 08:00（Asia/Shanghai），仅在后端运行时执行。

接口：

```text
GET  /api/providers/gmail/authorize
GET  /api/providers/gmail/callback
POST /api/providers/gmail/linkedin/import
```

refresh token 保存在被 Git 忽略的本地 `backend/storage/gmail-refresh-token` 文件中。

### 2.6 薪资提取

Greenhouse 和 LinkedIn 共用 `SalaryInfo` 解析逻辑：

- 支持 USD、SGD、CNY、HKD、GBP、EUR 等币种。
- 支持 hour、day、month、year 周期。
- 支持 `$120,000 - $180,000`、`SGD 6000-8000/month`、`¥20,000-30,000/月`、`25K-35K·14薪` 等常见形式。
- 没有明确薪资上下文时，不把普通数字误判为薪资。
- 结构化字段的来源标记为 `structured`。
- 从职位描述解析的来源标记为 `description`。
- 没有薪资时字段保持 null，卡片隐藏薪资区域。
- 前端通过统一 `formatSalary(job)` 格式化，不在不同来源中复制逻辑。

### 2.7 统一岗位卡片

当前所有来源在 `frontend/src/App.vue` 中使用同一套岗位卡片渲染逻辑，`source` 只决定来源标签：

- `LinkedIn`
- `Greenhouse`
- `Demo`

卡片包含：

- 来源和日期。
- 职位标题。
- 公司和地点。
- 薪资（存在时）。
- 结构化摘要。
- 技能标签。
- 远程/混合/现场办公标签（办公方式不明时不显示假标签）。
- `需审核` 标签。
- 零匹配分显示为“待匹配”，不伪造分数。
- 原始职位链接在新标签页打开。

列表当前只显示 `country=China` 或 `country=Singapore` 的岗位；其他国家和国家不明的记录仍保留在数据库中，但不展示在页面。

当前页面提供：

- 关键词搜索。
- 城市筛选。
- 来源筛选。
- 只看远程。
- 岗位数量和当前可审阅数量。

### 2.8 AI 语义分析与确定性匹配

本轮已完成从岗位导入到推荐排序的后端闭环：

1. Provider 先将岗位写入 `job_posting`，AI 失败不会丢弃岗位。
2. `DeepSeekAiGateway` 仅在后端调用配置的 OpenAI 兼容接口，发送标题、公司、地点和截断后的 JD；Key 不进入代码、前端、数据库或日志。
3. `SemanticAnalysisParser` 只接受严格 JSON/JSON code fence，拒绝额外文本和未知字段，校验枚举、数组、经验年限、nullable 字段，并要求 evidence 文本出现在原始 JD。
4. `job_ai_analysis`（Flyway V5）保存描述 SHA-256、分析版本、模型、状态、结构化技能/经验/办公方式/薪资/evidence。相同 JD + 版本 + 模型的成功结果直接命中缓存。
5. `JobMatchEngine` 在 Java 中按 30/25/20/15/10 五项固定权重计算 0–100 分；模型不能覆盖结果。国家不在 China/Singapore 或中国城市不在候选人允许列表时硬过滤，未知信息保持中性。
6. `job_match_result`（Flyway V6）保存组件分、匹配技能、缺失技能、正向理由、风险、硬过滤原因、候选人资料 hash 和算法版本。资料更新后会重新计算已有成功分析。
7. 前端“今日推荐”展示前 5 个已分析且通过硬过滤的岗位；卡片显示匹配理由/顾虑。未分析显示待分析，失败显示分析失败，不显示伪造分数。

手动接口：

```text
POST /api/ai/jobs/{id}/analyze
POST /api/ai/jobs/analyze-pending?limit=3
```

## 3. 当前 API

```text
GET  /api/jobs
GET  /api/candidate-profile
PUT  /api/candidate-profile
POST /api/providers/greenhouse/import
GET  /api/providers/gmail/authorize
GET  /api/providers/gmail/callback
POST /api/providers/gmail/linkedin/import
POST /api/ai/jobs/{id}/analyze
POST /api/ai/jobs/analyze-pending?limit=3
```

## 4. 当前数据与验证结果

最近一次本地验证结果：

- `/api/jobs` 返回 59 条岗位。
- 页面显示 41 条中国或新加坡岗位。
- 其他国家/国家不明记录被页面地区过滤隐藏。
- Greenhouse Airtable 幂等导入成功，导入 16 条。
- Greenhouse 完整 JD 仍保存，但卡片只显示摘要。
- `mvn test -q` 通过。
- `npm run build` 通过。
- `git diff --check` 通过。
- `mvn test -q` 通过，包含语义 JSON/evidence 校验和匹配引擎硬过滤测试。
- `npm run build` 通过。

## 5. DeepSeek / 硅基流动配置状态

项目根目录的本地 `.env` 已配置硅基流动参数，敏感值不写入此文档、不提交 Git：

```text
DEEPSEEK_BASE_URL=https://api.siliconflow.cn/v1
DEEPSEEK_MODEL=deepseek-ai/DeepSeek-V3.2
DEEPSEEK_API_KEY=<local secret, omitted>
```

已完成一次最小 SiliconFlow Chat Completions 连通性测试，返回 HTTP 200。

当前 `DeepSeekAiGateway` 已实现；默认不会批量消耗真实 API，需通过手动接口逐条或按上限分析。已实现超时、一次有限重试、严格 JSON/evidence 校验、哈希缓存和失败降级。未执行本轮全量真实岗位分析，以避免未经确认的 API 用量。

## 6. 明确未完成内容

- DeepSeek 分析的批量运营监控、失败重试队列和历史版本清理。
- 更高效的数据库分页/筛选（当前 API 在内存中完成筛选后分页）。
- BOSS 直连采集。
- LinkedIn 直接网页采集。
- Lever、Indeed 和其他来源的实际适配器。
- 职位详情页；目前通过数据库字段和原始链接保留完整 JD。
- 30 天原始数据清理任务。
- 跨来源模糊去重和重复发布历史。
- 应用记录、自动投递、登录代操作和验证码处理。
- 多用户认证、远程访问和云端部署。

## 7. 安全边界

- 不把 API Key、Gmail Client Secret、refresh token 提交到 Git。
- `.env` 和 Gmail token 文件已加入忽略规则。
- 服务默认只监听本机回环地址。
- Gmail 使用只读权限。
- 不保存密码，不代替用户登录，不处理验证码。
- 不自动申请职位，不提交表单。
- 外部 AI 接入前必须脱敏，原始简历文件保持本地。

## 8. 下一步建议

1. 在本地确认候选人资料后，用“分析待处理”小批量验证真实岗位结果。
2. 增加 provider 级限流、运行记录和失败重试队列。
3. 为更多稳定 ATS 增加适配器，并复用同一 UnifiedJob/AI/Match 流程。
4. 对人工审阅结果建立离线标注集，校准权重但不让模型直接改分。

## 9. 本地启动

先启动 Docker Desktop，然后：

```bash
cd "/Users/shj/Documents/学习/Find Work"
DOCKER_HOST=unix:///Users/shj/.docker/run/docker.sock docker compose -f infra/docker-compose.yml up -d postgres

cd backend
mvn spring-boot:run
```

另开终端启动前端：

```bash
cd "/Users/shj/Documents/学习/Find Work/frontend"
npm install
npm run dev
```

打开：<http://127.0.0.1:5173/>

## 10. 关键代码位置

```text
frontend/src/App.vue                         # 页面、筛选和统一岗位卡片
frontend/src/styles.css                      # 卡片和薪资样式
backend/.../job/JobPosting.java              # 统一职位模型
backend/.../job/JobPostingRepository.java    # PostgreSQL 查询和 upsert
backend/.../job/JobProviderService.java      # Greenhouse 导入和 normalize
backend/.../job/GmailLinkedInService.java   # Gmail OAuth、LinkedIn 解析
backend/.../job/SalaryInfo.java              # 共用薪资解析器
backend/.../job/DeepSeekAiGateway.java      # 后端 AI 网关与最小请求
backend/.../job/SemanticAnalysisParser.java  # 严格 JSON/enum/evidence 校验
backend/.../job/JobAnalysisService.java      # 分析缓存、失败降级与匹配编排
backend/.../job/JobMatchEngine.java          # 固定权重确定性匹配
backend/.../job/JobAiAnalysisRepository.java # V5 分析持久化
backend/.../job/JobMatchResultRepository.java # V6 匹配持久化
backend/.../job/ProviderController.java      # provider API
backend/.../job/JobPostingController.java    # GET /api/jobs
backend/src/main/resources/db/migration/    # Flyway V1–V6 迁移
```

## 11. 评审重点

后续架构或代码评审应重点关注：

1. DeepSeek 接入是否严格执行脱敏和证据约束。
2. 匹配算法是否保持确定性、可解释且不被 LLM 覆盖。
3. provider 失败是否只影响单个来源。
4. 原始数据保留和 30 天清理是否真正实现。
5. Greenhouse/LinkedIn 是否继续共用统一模型和卡片。
6. 只显示中国和新加坡的页面过滤是否与未来后端硬过滤策略保持一致。
