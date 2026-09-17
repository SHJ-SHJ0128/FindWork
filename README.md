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

