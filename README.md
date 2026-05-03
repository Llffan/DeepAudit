# DeepAudit · 病案首页质控 MVP

基于 Spring Boot 3 + Vue 3 + PostgreSQL 16 (pgvector) + langchain4j 的医院病案首页质控系统脚手架。

完整架构与决策见 `docs/病案首页与质控业务/`。本 README 只回答一件事：**怎么把这套环境跑起来**。

---

## 部署到新机器：5 步

### 0. 前置条件（一次性）

| 工具 | 用途 | 验证 |
|------|------|------|
| Docker Engine ≥ 24 + Compose v2 | 跑全部服务 | `docker compose version` |
| Git | 拉代码 | `git --version` |

> Windows 推荐 Docker Desktop（自带 Compose v2）；Linux 用官方 docker-ce + docker-compose-plugin。
> 不需要本机装 Java / Maven / Node / pnpm —— 全部在容器里构建。

### 1. 拉代码

```bash
git clone https://github.com/Llffan/DeepAudit.git
cd DeepAudit
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，填 DASHSCOPE_API_KEY=sk-xxxxxxxx
# 其他变量保留默认即可
```

`.env.example` 字段说明：

| 变量 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `DASHSCOPE_API_KEY` | ✅ | 空 | DashScope（阿里云通义）API key |
| `DASHSCOPE_DATA_IMPROVEMENT` | — | `off` | 控制台「数据改进计划」开关；保持 `off`，否则后端启动 WARN |
| `DB_HOST_PORT` | — | `25432` | Postgres 对外暴露端口（默认避开常见的 5432）|

### 3. 一键拉起

```bash
docker compose up -d --build
```

首次会拉镜像 + 构建后端/前端镜像，**约 5–10 分钟**（取决于网络）。

### 4. 等就绪 + 验证

```bash
# Linux / macOS / Git Bash
bash scripts/wait-for-stack.sh

# Windows PowerShell
pwsh scripts/wait-for-stack.ps1
```

脚本会轮询所有服务到 healthy。完成后：

```bash
curl http://localhost:8080/api/actuator/health
# 应返回 {"status":"UP","components":{"db":{"status":"UP",...
```

浏览器打开 **http://localhost:8080**（推荐 Chrome / Edge 最近两版）。

### 5. （可选）DashScope 三模型 smoke-test

```bash
export DASHSCOPE_API_KEY=$(grep DASHSCOPE_API_KEY .env | cut -d= -f2)
bash scripts/smoke-test/dashscope-all.sh
```

三个模型（qwen-max / qwen-vl-max / text-embedding-v3）全 PASS 即闭环。

---

## 服务拓扑

| 服务 | 镜像 | 容器内端口 | 主机暴露 | 说明 |
|------|------|----------|---------|------|
| `db` | `pgvector/pgvector:pg16` | 5432 | `25432` (可改 `DB_HOST_PORT`) | Postgres + pgvector 0.8.x，向量库 |
| `backend` | 本地构建 (Spring Boot 3.2.5 / JDK 21) | 8080 | — | REST + langchain4j + JDBC |
| `frontend` | 本地构建 (Vue 3.4 / Vite 5 / 静态资源) | 80 | — | 由 nginx 反代 |
| `nginx` | `nginx:1.27-alpine` | 8080 | **`8080`（唯一对外入口）** | 反向代理 + SSE 配置 |

数据持久化在 Docker named volume `deepaudit_db_data`，`docker compose down` 不会清空。

---

## 日常操作

```bash
docker compose ps              # 查看状态
docker compose logs -f backend # 跟踪后端日志
docker compose restart backend # 只重启后端
docker compose down            # 停止全部（保留数据）
docker compose down -v         # 停止并清空数据库（谨慎）
docker compose up -d --build   # 改了代码后重新构建并启动
```

---

## 常见问题

**Q: 端口 8080 / 25432 被占用？**
改 `docker-compose.yml` 里 `nginx.ports` 或 `.env` 里 `DB_HOST_PORT`。

**Q: Windows 上 `docker compose up` 报 "credsStore: specified logon session does not exist"？**
临时绕过：`mv ~/.docker/config.json ~/.docker/config.json.bak`，再跑一次，跑完恢复。或在 Docker Desktop 设置里关掉 "Use Docker Compose V2 with credential helper"。

**Q: `actuator/health` 返回 503？**
等更久些（`start_period` 30s 内是预热）。或 `docker compose logs backend` 看启动错误，常见是 `DASHSCOPE_API_KEY` 没填导致守卫拒绝启动。

**Q: 想离线部署到没网的机器？**
在有网的机器上：

```bash
docker compose build
docker save -o deepaudit-images.tar \
  pgvector/pgvector:pg16 \
  nginx:1.27-alpine \
  deepaudit-backend:phase0 \
  deepaudit-frontend:phase0
```

把 `deepaudit-images.tar` + 整个项目目录拷过去，目标机：

```bash
docker load -i deepaudit-images.tar
docker compose up -d   # 不加 --build，直接用导入的镜像
```

---

## 当前阶段

Phase 0（脚手架）已完成；Phase 1（数据层 + langchain4j 网关 + 规则引擎骨架）即将开始。详见 `../.planning/ROADMAP.md`（如果你拉了完整工作区）或本仓库的 `docs/`。
