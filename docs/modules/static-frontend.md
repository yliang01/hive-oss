# 静态前端模块

## 模块定位

基于原生 HTML/CSS/JavaScript 的 OSS 管理前端，提供 Bucket 列表与文件管理页面，通过 REST API 与后端 API 模块交互，实现列表、搜索、上传、下载、解冻、删除等操作的 UI。前端以**独立 Maven 模块** `hive-oss-frontend` 存在，由 `hive-oss` 后端依赖其 JAR，在运行时从 classpath 提供静态资源（同源、同一端口）。

## 启动 / 触发入口

- **开发态**：在 `hive-oss-frontend/src/main/resources/static` 目录下使用 `npx serve . --config serve.json` 等本地静态服务器；访问入口如 `pages/bucket.html`、`pages/files.html?bucket=xxx`。
- **运行态**：随 hive-oss Spring Boot 应用启动，静态资源来自依赖的 `hive-oss-frontend` JAR（classpath:/static/），由默认资源处理器提供，通过同一端口（如 8080）访问 `/pages/*`、`/js/*`、`/css/*` 等。

## 关键依赖

- **内部**：无；纯静态资源，依赖后端 REST API（见 `static/API.md`）。
- **外部**：Bootstrap 5.3.2、原生 ES Modules；后端接口基础 URL（如 `http://localhost:8080`）。

## 上下游关系

- **上游**：浏览器用户。
- **下游**：后端 API 模块（`HiveOssController` 暴露的 REST 接口）；不直接依赖服务编排或 OSS 适配层。

## 目录与文件

| 路径 | 说明 |
|------|------|
| `pages/bucket.html` | Bucket 列表页 |
| `pages/files.html` | 文件管理页（需 `bucket` 查询参数） |
| `js/bucket.js` | Bucket 列表逻辑 |
| `js/files.js` | 文件管理逻辑 |
| `js/utils.js` | 通用工具与 API 请求封装 |
| `css/style.css` | 样式 |
| `serve.json` | 本地 serve 配置 |
| `README.md` | 前端说明与开发命令 |
| `API.md` | 接口文档（与后端接口一致） |

## 关键配置项

- 无构建或环境变量；接口基础 URL 可在前端代码或配置中写死或通过相对路径依赖同源。

## 相关文件

- **独立模块**：`hive-oss-frontend`（与 `hive-oss` 同级）。
- **静态资源路径**：`hive-oss-frontend/src/main/resources/static/` 下所有页面、脚本、样式与文档（`pages/`、`js/`、`css/`、`README.md`、`API.md`、`serve.json`）。

---

[返回模块总览](README.md)
