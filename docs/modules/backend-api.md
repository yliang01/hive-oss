# 后端 API 模块

## 模块定位

对外暴露 REST 接口，承接前端与调用方请求，完成 Bucket/文件列表、搜索、上传、下载、解冻、删除及同步等操作的入口与响应封装。

## 启动 / 触发入口

- **HTTP 入口**：`cc.cc3c.hive.oss.controller.HiveOssController`（`@RestController`）
- 随 Spring Boot 应用启动加载；无独立 main，通过 `HiveOssApplication` 启动后由 DispatcherServlet 路由到本 Controller。

## 关键依赖

- **内部**：`HiveOssService`、`HiveUploadService`、`HiveDownloadService`、`HiveSyncService`、`HiveRecordRepository`；VO 位于 `cc.cc3c.hive.oss.controller.vo`（如 `HiveRecordVO`、`HiveRecordsVO`、`HiveUploadVO`、`HiveSyncVO`、`HiveDownloadStatusVO`）。
- **外部**：`hive-domain`（`HiveRecord`、`HiveRecordSource`、`HiveRecordStatus`、`HiveDownloadStatus`、`HiveRecordRepository`）；Spring Web、Validation、Commons FileUpload2（multipart 上传）。

## 上下游关系

- **上游**：静态前端（`hive-oss-frontend` 模块 `static/` 下页面与 JS）、其他 HTTP 客户端。
- **下游**：服务编排层（四个 Service）、领域仓储（`HiveRecordRepository`）；上传接口直接使用 `HiveUploadService.uploadSync`，其余操作经各 Service 再访问 OSS 适配层与 DB。

## 主要接口（与 hive-oss-frontend 中 API.md 对应）

| 能力 | 方法 | 路径（示例） |
|------|------|----------------|
| Bucket 列表 | GET | `/buckets` |
| 文件列表 | GET | `/buckets/{bucket}/files` |
| 文件搜索 | GET | `/buckets/{bucket}/files/search` |
| 单文件信息 | GET | `/buckets/{bucket}/files/{fileKey}` |
| 删除文件 | DELETE | `/buckets/{bucket}/files/{fileKey}` |
| 解冻 | POST | `/buckets/{bucket}/files/unfreeze/{fileKey}` |
| 解冻状态 | GET | `/buckets/{bucket}/files/unfreeze-status/{fileKey}` |
| 创建下载任务 | POST | `/buckets/{bucket}/files/download-task/{fileKey}` |
| 下载任务状态 | GET | `/buckets/{bucket}/files/download-task-status/{fileKey}` |
| 释放本地文件 | POST | `/buckets/{bucket}/files/release-local/{fileKey}` |
| 确认删除 | POST | `/files/confirm-delete/{bucket}` |
| 远程同步 | POST | `/files/sync-remote/{bucket}` |
| 上传 | POST | `/buckets/{bucket}/files/upload`（multipart） |

路径中的 `bucket` 对应 `HiveRecordSource` 枚举名（如 `ALIBABA_STANDARD`、`ALIBABA_ACHIEVE`）。

## 关键配置项

- 无模块专属配置 key；依赖全局 `hive.uploadDir`、`hive.downloadDir` 及 Spring 多部分上传限制（Controller 内 100MB）。

## 相关文件

- `src/main/java/cc/cc3c/hive/oss/controller/HiveOssController.java`
- `src/main/java/cc/cc3c/hive/oss/controller/vo/*.java`
- `src/main/java/cc/cc3c/hive/oss/config/HiveOssWebConfig.java`（CORS 等 Web 配置）

---

[返回模块总览](README.md)
