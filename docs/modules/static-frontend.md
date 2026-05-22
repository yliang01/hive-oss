# 静态前端模块

## 模块定位

基于原生 HTML/CSS/JavaScript 的 OSS 管理前端，提供 Bucket 列表与文件管理页面，通过 REST API 与后端 API 模块交互，实现列表、搜索、上传、下载、解冻、删除等操作的 UI。前端以**独立 Maven 模块** `hive-oss-frontend` 存在，由 `hive-oss` 后端依赖其 JAR，在运行时从 classpath 提供静态资源（同源、同一端口）。

## 启动 / 触发入口

- **开发态**：在 `hive-oss-frontend/frontend-app` 目录下使用 `npm run dev` 启动 Vite；或先构建后在 `hive-oss-frontend/target/generated-resources/static` 目录下使用 `npx serve . --config serve.json`。
- **运行态**：随 hive-oss Spring Boot 应用启动，静态资源来自依赖的 `hive-oss-frontend` JAR（classpath:/static/），由默认资源处理器提供，通过同一端口（如 8080）访问 `/index.html`、`/pages/*`、`/assets/*` 等。

## 关键依赖

- **内部**：无；纯静态资源，依赖后端 REST API。
- **外部**：Bootstrap 5.3.2、原生 ES Modules；后端接口基础 URL（如 `http://localhost:8080`）。

## 上下游关系

- **上游**：浏览器用户。
- **下游**：后端 API 模块（`HiveOssController` 暴露的 REST 接口）；不直接依赖服务编排或 OSS 适配层。

## 目录与文件

| 路径 | 说明 |
|------|------|
| `index.html` | 首页 |
| `pages/files.html` | 文件管理页（需分类查询参数） |
| `pages/category-admin.html` | 分类与分组管理页 |
| `js/home.js` | 首页逻辑 |
| `js/files.js` | 文件管理逻辑 |
| `js/category-admin.js` | 分类与分组管理逻辑 |
| `js/utils.js` | 通用工具与 API 请求封装 |
| `css/style.css` | 样式 |
| `serve.json` | 本地 serve 配置 |
| `README.md` | 前端说明与开发命令 |

## 关键配置项

- 无构建或环境变量；接口基础 URL 可在前端代码或配置中写死或通过相对路径依赖同源。

## 相关文件

- **独立模块**：`hive-oss-frontend`（与 `hive-oss` 同级）。
- **源码路径**：`hive-oss-frontend/frontend-app/` 下的页面、脚本、样式与 `public/serve.json`。
- **构建产物路径**：`hive-oss-frontend/target/generated-resources/static/`，由 Maven 打包进 `hive-oss-frontend` JAR。

---

[返回模块总览](README.md)
