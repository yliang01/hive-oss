# 静态前端模块

## 模块定位

基于 Vue 3、Vue Router、Pinia、Element Plus 和 Vite 的 OSS 管理前端，提供分类入口、文件管理、分类管理、数据库备份/恢复等 UI。前端以**独立 Maven 模块** `hive-oss-frontend` 存在，由 `hive-oss` 后端依赖其 JAR，在运行时从 classpath 提供静态资源（同源、同一端口）。

## 启动 / 触发入口

- **开发态**：在 `hive-oss-frontend/frontend-app` 目录下使用 `npm run dev` 启动 Vite；或先构建后在 `hive-oss-frontend/target/generated-resources/static` 目录下使用 `npx serve . --config serve.json`。
- **运行态**：随 hive-oss Spring Boot 应用启动，静态资源来自依赖的 `hive-oss-frontend` JAR（classpath:/static/），由默认资源处理器提供，通过同一端口（如 8080）访问 `/index.html`、`/assets/*` 等。页面使用 hash 路由，例如 `/#/files/{category}`、`/#/admin/categories`。

## 关键依赖

- **内部**：无；构建后为静态资源，依赖后端 REST API。
- **外部**：Vue 3、Vue Router、Pinia、Element Plus、Axios；后端接口基础 URL 默认同源，可通过 `VITE_API_BASE` 配置。

## 上下游关系

- **上游**：浏览器用户。
- **下游**：后端 API 模块（`HiveOssController` 暴露的 REST 接口）；不直接依赖服务编排或 OSS 适配层。

## 目录与文件

| 路径 | 说明 |
|------|------|
| `index.html` | Vue SPA 入口 |
| `src/router/` | hash 路由配置 |
| `src/views/` | 首页、文件页、分类管理页 |
| `src/stores/` | Pinia 状态与轮询逻辑 |
| `src/api/` | REST API 请求封装 |
| `src/components/` | 上传、预览、高亮等复用组件 |
| `serve.json` | 本地 serve 配置 |
| `README.md` | 前端说明与开发命令 |

## 关键配置项

- `VITE_API_BASE`：可选 API 基础路径，默认空字符串表示同源。

## 相关文件

- **独立模块**：`hive-oss-frontend`（与 `hive-oss` 同级）。
- **源码路径**：`hive-oss-frontend/frontend-app/` 下的 Vue 源码、Vite 配置与 `public/serve.json`。
- **构建产物路径**：`hive-oss-frontend/target/generated-resources/static/`，由 Maven 打包进 `hive-oss-frontend` JAR。

---

[返回模块总览](README.md)
