# FindWork

FindWork 是本地运行的求职岗位收集与匹配工具。当前已完成候选人资料基础页；后续岗位采集、匹配和 Gmail/DeepSeek 集成按 [PLAN.md](PLAN.md) 分阶段实现。

## 本地打开方式

不要双击 `frontend/index.html`，`file://` 只会加载静态文件，无法正确连接本地 API。使用 Vite 开发服务器：

```bash
cd "/Users/shj/Documents/学习/Find Work/frontend"
npm install
npm run dev
```

然后打开 <http://127.0.0.1:5173/>。

## 导入真实岗位

前端页面的“导入真实岗位”面板目前支持公开的 Greenhouse Job Board，不需要账号、密码或自动申请。输入岗位板 slug（例如 `airtable`）或完整 URL（例如 `https://boards.greenhouse.io/airtable`），点击“导入 Greenhouse”即可写入本地数据库；重复导入会更新已有记录，不会重复计数。

也可以直接调用后端接口：

```bash
curl -X POST http://127.0.0.1:8080/api/providers/greenhouse/import \
  -H 'Content-Type: application/json' \
  -d '{"board":"airtable"}'
```

岗位卡片会使用 Greenhouse 返回的 `absolute_url` 作为原始职位链接，并在新标签页打开。Greenhouse 的完整 JD 保存在 `description`，列表仅显示统一 `summary`；可识别的结构化或文本薪资会保存为统一薪资字段并展示。列表当前只显示中国和新加坡岗位，其他国家及国家不明的记录仍保存在数据库中但不进入页面。当前导入记录仅用于人工筛选，BOSS 直连和自动投递尚未接入。

## 同步 LinkedIn 岗位提醒

LinkedIn 不提供面向普通求职者的公开岗位搜索 API，因此 FindWork 通过 Gmail API 的 `gmail.readonly` 权限读取 LinkedIn 岗位提醒邮件。首次使用前：

1. 在 Google Cloud 创建 OAuth Client，启用 Gmail API，并将回调地址配置为 `http://127.0.0.1:8080/api/providers/gmail/callback`。
2. 将 `.env.example` 复制为未跟踪的 `.env`，填写 `GMAIL_CLIENT_ID` 和 `GMAIL_CLIENT_SECRET`。
3. 启动后端，在页面点击“连接 Gmail”并完成授权；refresh token 只保存到被 Git 忽略的 `storage/gmail-refresh-token`。
4. 点击“立即同步 LinkedIn 邮件”验证导入。默认只查询最近 7 天的 LinkedIn 邮件，可通过 `GMAIL_LINKEDIN_QUERY` 调整。

后端每天 08:00（`GMAIL_TIME_ZONE`）同步一次，但应用关闭时不会后台运行。导入时会按每个职位链接拆分邮件条目，整理标题、公司、国家、城市、办公方式、经验层级、技能标签和可识别薪资；无法从邮件确认的字段会保留“待确认”，匹配分在匹配模块完成前显示“待匹配”。岗位使用现有 `canonical_url` 保存 LinkedIn 原始职位链接，并标记为“需审核”；不会自动申请。BOSS 保持独立的后续 provider，不会通过 Gmail 伪装接入。

## 启动后端（需要 PostgreSQL）

先启动 Docker Desktop，再执行：

```bash
cd "/Users/shj/Documents/学习/Find Work"
DOCKER_HOST=unix:///Users/shj/.docker/run/docker.sock docker compose -f infra/docker-compose.yml up -d postgres
cd backend
mvn spring-boot:run
```

后端地址为 <http://127.0.0.1:8080>。如果暂时不启动后端，前端仍可打开和编辑默认资料，但保存会提示无法连接后端。

## 验证

```bash
cd "/Users/shj/Documents/学习/Find Work/backend" && mvn test
cd "/Users/shj/Documents/学习/Find Work/frontend" && npm run build
```
