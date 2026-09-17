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

岗位卡片会使用 Greenhouse 返回的 `absolute_url` 作为原始职位链接，并在新标签页打开。当前导入记录仅用于人工筛选，LinkedIn、BOSS、Gmail 同步和自动投递尚未接入。

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
