# Panda-Mini-Brain（Panda 知识库）

一个面向个人/团队的轻量知识库：文件上传与预览、OCR、RAG 问答、流式对话、分享链接（可选有效期）。

## 功能特色

- 文件管理：上传/删除/移动/目录管理，支持预览与下载（MinIO 存储）
- OCR 识别：图片/扫描件提取文字（Baidu OCR）
- RAG 问答：基于知识库检索增强生成，支持 SSE 流式输出
- 会话管理：聊天会话与历史消息（MongoDB）
- 向量检索：pgvector 作为向量存储（PostgreSQL）
- 分享链接：可设置永久 / 7 天 / 30 天；分享者可继续对话，访客只读（禁文件操作）

## 技术栈

- 前端：Vue 3 + Vite + Element Plus + Pinia + Axios（JavaScript）
- 后端：Spring Boot 3（Java 17）+ LangChain4j（对接豆包/火山引擎兼容接口）
- 存储：MongoDB（会话/元数据）、PostgreSQL + pgvector（向量）、MinIO（对象存储）
- 部署：Docker + docker-compose + Nginx（前端静态 + 反向代理 + HTTPS）

## 本地开发

### 1) 启动后端（Spring Boot）

在项目根目录：

```bash
mvn spring-boot:run
```

默认端口 `8080`，关键配置在 [application.yml](file:///e:/java%E6%89%8D%E6%98%AF%E7%9C%9F%E7%A5%9E/Java%E9%A1%B9%E7%9B%AE/Panda-Mini-Brain/src/main/resources/application.yml)。

### 2) 启动前端（Vite）

在 `frontend/` 目录：

```bash
npm install
npm run dev
```

前端请求基址在 [request.js](file:///e:/java%E6%89%8D%E6%98%AF%E7%9C%9F%E7%A5%9E/Java%E9%A1%B9%E7%9B%AE/Panda-Mini-Brain/frontend/src/api/request.js) 内动态解析：

- 域名是 `tongzhilian.cn / www.tongzhilian.cn` 时，走 `https://api.tongzhilian.cn`
- 其他情况（本地开发）走相对路径 `/`，通常由本地代理或同源后端处理

## 生产部署（docker-compose + Nginx + HTTPS）

项目根目录已提供 [docker-compose.yml](file:///e:/java%E6%89%8D%E6%98%AF%E7%9C%9F%E7%A5%9E/Java%E9%A1%B9%E7%9B%AE/Panda-Mini-Brain/docker-compose.yml)，包含 5 个服务：`app / web / minio / mongodb / postgres(pgvector)`。

### 1) 准备环境变量

建议使用 `.env`（不要提交到仓库）提供关键变量：

- `VOLC_API_KEY`：火山引擎（豆包）API Key（必需）
- `BAIDU_OCR_API_KEY`、`BAIDU_OCR_SECRET_KEY`：Baidu OCR（如启用 OCR 必需）
- `PANDA_MAIL_USERNAME`、`PANDA_MAIL_PASSWORD`：邮件验证码发送（如启用邮箱登录必需）
- `POSTGRES_USER`、`POSTGRES_PASSWORD`、`MINIO_ROOT_USER`、`MINIO_ROOT_PASSWORD`：生产环境务必改成强密码
- `MINIO_PUBLIC_ENDPOINT`：对外访问 MinIO 的 HTTPS 域名（见下方 Nginx 逻辑）

### 2) 准备 HTTPS 证书（容器挂载）

`web` 服务会把证书挂载到容器内：

- `/etc/nginx/ssl/tongzhilian.cn.pem`、`/etc/nginx/ssl/tongzhilian.cn.key`
- `/etc/nginx/ssl/api.tongzhilian.cn.pem`、`/etc/nginx/ssl/api.tongzhilian.cn.key`

对应宿主机路径在 `docker-compose.yml` 里写死为 `/opt/panda/...`，按你的服务器实际情况放置并调整。

### 3) 一键启动

在项目根目录：

```bash
docker compose up -d --build
```

默认映射：

- 前端：`80/443 -> web`
- 后端：`8080 -> app`
- MinIO：`9000/9001 -> minio`
- MongoDB：`27017 -> mongodb`
- PostgreSQL：`5432 -> postgres`

## Nginx 逻辑（重点）

Nginx 配置在 [frontend/nginx.conf](file:///e:/java%E6%89%8D%E6%98%AF%E7%9C%9F%E7%A5%9E/Java%E9%A1%B9%E7%9B%AE/Panda-Mini-Brain/frontend/nginx.conf)，核心目标是：

1) `tongzhilian.cn`：提供前端静态页面，并把 `/api/` 与 `/ai/` 反向代理到后端  
2) `api.tongzhilian.cn`：作为统一 API 域名，同时把 MinIO 通过 HTTPS 暴露出去，避免浏览器 Mixed Content

### 域名与转发规则

- `tongzhilian.cn`（80 -> 301 到 443）
  - `/`：前端静态 + SPA 路由回退 `try_files ... /index.html`
  - `/api/`：转发到 `http://app:8080`
  - `/ai/`：转发到 `http://app:8080`（SSE 流式接口已关闭代理缓冲）

- `api.tongzhilian.cn`（80 -> 301 到 443）
  - `/api/`：转发到 `http://app:8080`
  - `/ai/`：转发到 `http://app:8080`
  - `/panda-files/`：转发到 `http://minio:9000`
  - `/`：兜底转发到 `http://app:8080`

### 为什么能修复 MinIO Mixed Content

后端对外返回文件预签名 URL 时，用的是一个“专门用于签名的 MinIO Client”，它的 endpoint 优先读取 `panda.minio.public-endpoint`（环境变量 `MINIO_PUBLIC_ENDPOINT`）：

- 代码位置：[MinioConfig.java](file:///e:/java%E6%89%8D%E6%98%AF%E7%9C%9F%E7%A5%9E/Java%E9%A1%B9%E7%9B%AE/Panda-Mini-Brain/src/main/java/org/AI/panda/config/MinioConfig.java)
- 预签名生成：[MinioService#getPresignedUrl](file:///e:/java%E6%89%8D%E6%98%AF%E7%9C%9F%E7%A5%9E/Java%E9%A1%B9%E7%9B%AE/Panda-Mini-Brain/src/main/java/org/AI/panda/service/MinioService.java#L122-L141)

生产环境推荐：

- `MINIO_ENDPOINT=http://minio:9000`（容器内访问）
- `MINIO_PUBLIC_ENDPOINT=https://api.tongzhilian.cn`（浏览器访问）

这样前端页面在 HTTPS 下拿到的文件链接也是 HTTPS，避免 Mixed Content。

## 分享链接逻辑

### 链接格式

- 前端页面：`https://tongzhilian.cn/?shareToken=xxxxx`
- 后端也支持 Header：`X-Share-Token: xxxxx`

### 权限边界

- 解析与注入：后端过滤器 [ShareTokenFilter](file:///e:/java%E6%89%8D%E6%98%AF%E7%9C%9F%E7%A5%9E/Java%E9%A1%B9%E7%9B%AE/Panda-Mini-Brain/src/main/java/org/AI/panda/auth/web/ShareTokenFilter.java) 会把分享者 `ownerUserId` 与 `sessionId` 注入到 request attribute
- 统一取用户：业务层通过 [UserIdResolver](file:///e:/java%E6%89%8D%E6%98%AF%E7%9C%9F%E7%A5%9E/Java%E9%A1%B9%E7%9B%AE/Panda-Mini-Brain/src/main/java/org/AI/panda/auth/web/UserIdResolver.java) 优先使用分享者身份，保证“打开分享链接看到的是分享人的会话”
- 只读限制：当 `shareToken` 存在且访问者不是分享者时，`isVisitor()` 为 `true`，文件管理等接口直接 `403`

### 过期时间

创建分享接口支持 `ttlDays`：

- `ttlDays <= 0`：永久
- `ttlDays = 7`：7 天
- `ttlDays = 30`：30 天

实现位置：

- [ChatSessionController#createShare](file:///e:/java%E6%89%8D%E6%98%AF%E7%9C%9F%E7%A5%9E/Java%E9%A1%B9%E7%9B%AE/Panda-Mini-Brain/src/main/java/org/AI/panda/controller/ChatSessionController.java#L75-L91)
- [ShareLinkService](file:///e:/java%E6%89%8D%E6%98%AF%E7%9C%9F%E7%A5%9E/Java%E9%A1%B9%E7%9B%AE/Panda-Mini-Brain/src/main/java/org/AI/panda/auth/service/ShareLinkService.java)

## 接口返回格式

后端统一返回：

```json
{ "code": 200, "message": "OK", "data": {} }
```

实现位置：[Result.java](file:///e:/java%E6%89%8D%E6%98%AF%E7%9C%9F%E7%A5%9E/Java%E9%A1%B9%E7%9B%AE/Panda-Mini-Brain/src/main/java/org/AI/panda/common/Result.java)

