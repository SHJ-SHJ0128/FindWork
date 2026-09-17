# FindWork 项目现状与已完成工作

更新时间：2026-09-18
当前分支：`codex/increase-demo-jobs`
当前 PR：[PR #1](https://github.com/SHJ-SHJ0128/FindWork/pull/1)，代码已推送

## 1. 项目目标

FindWork 是一个本地运行的个人求职岗位收集与人工审阅工具，目标是：

- 收集中国和新加坡的软件工程相关岗位。
- 保留较高召回率，让用户先审阅数量，再判断质量。
- 将不同来源的职位转换为统一数据结构。
- 保存原始职位链接，方便人工打开和申请。
- 对不确定字段进行标记，不伪造职位信息；当前不生成匹配度分数。

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
- `V7__remove_demo_jobs.sql`：删除所有 `source=DEMO` 的历史演示岗位，避免新旧数据库继续展示虚构职位。

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
- 原始职位链接在新标签页打开。

列表当前只显示 `country=China` 或 `country=Singapore` 的岗位；其他国家和国家不明的记录仍保留在数据库中，但不展示在页面。

当前页面提供：

- 关键词搜索。
- 城市筛选。
- 来源筛选。
- 只看远程。
- 岗位数量和当前可审阅数量。

### 2.8 匹配度分析状态

匹配度分析和 DeepSeek 自动分析已从当前产品流程移除：

1. `GET /api/jobs` 直接返回统一 `JobPosting`，不附加 AI 分析状态或匹配结果。
2. 岗位列表不调用 AI，不显示匹配分、推荐分、证据或匹配理由。
3. 保存候选人资料不会触发重新匹配，后端也没有后台 AI 分析任务。
4. Flyway V5/V6 表、旧分析类和测试暂时保留，作为已有本地数据的兼容遗留；当前运行流程不读写这些结果。

### 2.9 已确认的下一阶段 AI 方案（部分实施）

2026-09-18 已完成产品确认。Resume AI 的本地上传、文字提取和职位 JD 按需整理首个切片已完成；Gmail Triage 尚未实现。后续 Sprint 的边界为：

- 同时支持 LinkedIn Gmail 职位提醒整理、PDF/DOCX 简历解析和新导入职位 JD 分析。
- 简历上传后自动分析；保留历史版本和一个当前版本；资料变更以字段级差异呈现，用户确认后才写入。
- 完整简历只保存在本机，发送到硅基流动 DeepSeek 前脱敏；职位描述可以发送。
- Gmail 只处理 LinkedIn 职位提醒。成功导入后可以标记已读，但不归档、删除或移动；当前代码尚未实现标记已读，仍为只读流程。
- 新职位自动分析仍是目标；当前通过岗位卡片显式按需分析，输出结构化标签、要求清单、职责、证据片段和状态，且以描述 hash 缓存。
- AI 失败不阻塞职位导入，标记待分析/失败并允许重试；保存结构化结果和内容哈希，不长期保存完整 Prompt/Response。
- 不生成数字匹配度分数，不改变现有筛选、排序或岗位数量；自动投递、代登录、验证码和求职信生成不在范围内。

实现顺序确定为：Resume AI → Job AI → Gmail Triage。岗位运行流程不会调用 DeepSeek；Resume AI 外部分析仅在显式打开 `DEEPSEEK_ENABLED` 后执行，默认值仍为 `false`。

### 2.10 Resume AI 首个切片

- `V8__candidate_resume.sql` 新增本地简历元数据、提取文本、SHA-256 去重、单个当前版本和分析记录。
- `POST /api/resumes` 只接受 PDF/DOCX，校验文件签名和 10MB 上限；PDF 使用 PDFBox，DOCX 使用 Apache POI。
- 原件保存于被 Git 忽略的 `backend/storage/resumes/`，接口不会返回完整简历正文。
- 上传后在 AI 开启时自动分析；AI 建议支持技能、目标职位和经验字段的勾选确认，再应用到候选人资料。
- 扫描型 PDF 或空文本会返回明确的文字提取失败提示，不伪造分析结果。

### 2.11 Job AI 首个切片

- `DeepSeekAiGateway` 已注册为可选网关；默认仍由 `DEEPSEEK_ENABLED=false` 关闭。
- `GET/POST /api/jobs/{id}/analysis` 提供当前结果和显式触发分析；内容 hash、模型和版本不变时复用成功结果。
- 岗位卡片显示职位类别、必备/加分技能和最多三项职责；失败显示可重试状态，不写入匹配分，也不改变岗位排序、筛选和数量。

## 3. 当前 API

```text
GET  /api/jobs
GET  /api/jobs/{id}/analysis
POST /api/jobs/{id}/analysis
GET  /api/candidate-profile
PUT  /api/candidate-profile
POST /api/providers/greenhouse/import
GET  /api/providers/gmail/authorize
GET  /api/providers/gmail/callback
POST /api/providers/gmail/linkedin/import
POST /api/resumes
GET  /api/resumes/current
GET  /api/resumes/{id}
POST /api/resumes/{id}/analyze
POST /api/resumes/{id}/apply-to-profile
DELETE /api/resumes/{id}
```

## 4. 当前数据与验证结果

最近一次本地验证结果：

- `/api/jobs` 返回 44 条岗位。
- 页面显示 27 条中国或新加坡岗位（其余真实记录因地区规则隐藏）。
- 数据库中 `GREENHOUSE=16`、`LINKEDIN=28`、`DEMO=0`。
- 其他国家/国家不明记录被页面地区过滤隐藏。
- Greenhouse Airtable 幂等导入成功，导入 16 条。
- Greenhouse 完整 JD 仍保存，但卡片只显示摘要。
- 页面加载不调用 AI；点击岗位卡片后显示 JD 结构化整理，不显示匹配度或推荐分。
- `mvn test -q` 通过。
- `npm run build` 通过。
- `git diff --check` 通过。

## 5. DeepSeek / 硅基流动配置状态

项目根目录的本地 `.env` 已配置硅基流动参数，敏感值不写入此文档、不提交 Git：

```text
DEEPSEEK_BASE_URL=https://api.siliconflow.cn/v1
DEEPSEEK_MODEL=deepseek-ai/DeepSeek-V3.2
DEEPSEEK_API_KEY=<local secret, omitted>
```

此前已完成一次最小 SiliconFlow Chat Completions 连通性测试，返回 HTTP 200。Resume AI 和按需 Job AI 网关已接入，只有显式启用 `DEEPSEEK_ENABLED=true` 才会发送脱敏简历文本或岗位描述，默认值为 `false`。历史匹配类和 V5/V6 表暂时保留用于本地数据兼容。

## 6. 明确未完成内容

- 更高效的数据库分页/筛选（当前 API 在内存中完成筛选后分页）。
- Resume AI 的更完整字段模型、证据展示和生产级异步队列。
- 新职位自动 JD 分析调度（当前为卡片按需触发）和更完整的证据展示。
- Gmail LinkedIn 邮件去重后的自动标记已读。
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
- 下一阶段允许成功导入 LinkedIn 邮件后标记已读，但当前实现仍保持 Gmail 只读，未执行该写操作。

## 8. 下一步建议

1. 按已确认顺序实现 Resume AI、Job AI、Gmail Triage，并单独验证隐私、失败回退和证据链。
2. 增加 provider 级限流、运行记录和失败重试队列。
3. 为更多稳定 ATS 增加适配器，并复用同一 UnifiedJob 流程。
4. 对人工审阅结果建立离线标注集；如需恢复评分，必须重新进行产品决策。

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
backend/.../job/DeepSeekAiGateway.java      # SiliconFlow/DeepSeek JD 网关
backend/.../job/SemanticAnalysisParser.java  # JD 结构化结果校验
backend/.../job/JobIntelligenceService.java  # 按需 JD 分析编排（无匹配分）
backend/.../job/JobIntelligenceController.java # JD 分析 REST 接口
backend/.../job/JobMatchEngine.java          # 已停用的历史匹配逻辑
backend/.../job/JobAiAnalysisRepository.java # V5 历史数据兼容
backend/.../job/JobMatchResultRepository.java # V6 历史数据兼容
backend/.../job/ProviderController.java      # provider API
backend/.../job/JobPostingController.java    # GET /api/jobs
backend/src/main/resources/db/migration/    # Flyway V1–V6 迁移
```

## 11. 评审重点

后续架构或代码评审应重点关注：

1. provider 失败是否只影响单个来源。
2. 原始数据保留和 30 天清理是否真正实现。
3. Greenhouse/LinkedIn 是否继续共用统一模型和卡片。
4. 只显示中国和新加坡的页面过滤是否与未来后端硬过滤策略保持一致。
