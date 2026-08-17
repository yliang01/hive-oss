# 后端 API 模块

## 模块定位

对外暴露 REST 接口，承接前端与调用方请求，完成 Bucket/文件列表、搜索、上传、下载、解冻、删除及同步等操作的入口与响应封装。

## 启动 / 触发入口

- **HTTP 入口**：`HiveFileController`、`HiveFileArchiveController`、`HiveFileDownloadController` 等 `@RestController`。
- 随 Spring Boot 应用启动加载；无独立 main，通过 `HiveOssApplication` 启动后由 DispatcherServlet 路由到各 Controller。

## 关键依赖

- **内部**：`HiveOssService`、`HiveUploadService`、`HiveDownloadService`、`HiveSyncService`、`HiveRecordRepository`；VO 位于 `cc.cc3c.hive.oss.controller.vo`（如 `HiveRecordVO`、`HiveRecordsVO`、`HiveUploadVO`、`HiveSyncVO`、`HiveDownloadStatusVO`）。
- **外部**：`hive-domain`（`HiveRecord`、`HiveStorageProvider`、`HiveRecordStatus`、`HiveDownloadStatus`、`HiveRecordRepository`）；Spring Web、Validation、Commons FileUpload2（multipart 上传）。

## 上下游关系

- **上游**：静态前端（`hive-oss-frontend` 模块 `static/` 下页面与 JS）、其他 HTTP 客户端。
- **下游**：服务编排层（四个 Service）、领域仓储（`HiveRecordRepository`）；上传接口直接使用 `HiveUploadService.uploadSync`，其余操作经各 Service 再访问 OSS 适配层与 DB。

## 主要接口（与 hive-oss-frontend 中 API.md 对应）

| 能力 | 方法 | 路径（示例） |
|------|------|----------------|
| 分类列表 | GET | `/categories` |
| 文件列表（含搜索/分组筛选） | GET | `/categories/{category}/files` |
| 批量获取文件详情 | GET | `/categories/{category}/files:batch-get?fileKeys=...` |
| 标记删除文件 | POST | `/categories/{category}/files:mark-delete` |
| 解冻 | POST | `/categories/{category}/files/unfreeze/{fileKey}` |
| 解冻状态 | GET | `/categories/{category}/files/unfreeze-status/{fileKey}` |
| 创建下载任务 | POST | `/categories/{category}/files/download-task/{fileKey}` |
| 下载任务状态 | GET | `/categories/{category}/files/download-task-status/{fileKey}` |
| 分组分配（主） | POST | `/categories/{category}/groups/{groupId}/files:assign` |
| 分组迁移（兼容别名，Deprecated） | POST | `/categories/{category}/groups/{groupId}/files:move` |
| 释放本地文件 | POST | `/categories/{category}/files/release-local/{fileKey}` |
| 确认删除 | POST | `/categories/{category}/files/confirm-delete` |
| 远程同步 | POST | `/categories/{category}/files/sync-remote` |
| 上传 | POST | `/categories/{category}/files/upload`（multipart） |

路径中的 `category` 映射到 `file_category.code`，再由 category 的 `bucket_name` 决定具体 OSS bucket。

## 解冻与下载契约

解冻只适用于 `storageClass=ARCHIVE`，其他分类的解冻与状态查询返回 `400 Bad Request`。`restorable` 表示归档对象是否仍被冻结：当 `unfreezeTime` 为空或已过期时为 `true`。前端仅在归档分类显示解冻操作，`unfreezeStatus=IN_PROGRESS` 用于页面刷新后恢复轮询。

`POST /files/unfreeze/{fileKey}` 和 `GET /files/unfreeze-status/{fileKey}` 都先向 OSS 查询恢复状态：`NOT_STARTED` 表示尚未申请，`IN_PROGRESS` 返回 `202 Accepted`，`COMPLETED` 返回 `200 OK` 并记录 OSS 给出的有效截止时间。发起解冻时服务端写入 `IN_PROGRESS`；完成时写入 `COMPLETED` 和 `unfreezeTime`。只有有效期内的归档对象可以下载。

`/categories/{category}/files/search` 兼容期建议保留 2 个发布周期，期间通过响应头 `X-API-Deprecated` 提示调用方迁移到 `/categories/{category}/files?keyword=...`。

`/categories/{category}/files/download-task-status/{fileKey}` 返回下载任务状态与进度；成功态不返回下载目录物理路径或文件存在状态。文件列表中的 `localFileAvailable` 由后端按文件系统实时计算，仅用于控制下载和释放操作。

创建下载任务的资格由 `HiveDownloadService` 统一判断，文件列表的 `downloadable` 使用同一规则：记录必须为 `UPLOADED`、非冻结归档、非 `downloading`，且目标本地副本不存在。冻结归档返回 `423 Locked`；其他不满足条件的请求返回 `409 Conflict`，响应体为 `{ "error": "..." }`。因此直接调用 API 不能绕过 UI 按钮限制。

创建任务前会修正过期的 `downloading` 状态：若内存任务已不存在，服务端删除遗留 `.part` 文件、将 DB 状态更新为 `failed`，并在当前请求继续创建新任务。任务启动后状态为 `downloading`，成功落盘后为 `success`，失败后为 `failed`。释放本地文件只删除常规文件并清空下载状态；下载中释放仍返回 `409 Conflict`。

下载副本路径按 `hive.downloadDir/{categoryCode}/{groupCode 或 _ungrouped}/{fileName}[.zip]` 组织。存在下载副本或下载中的文件不能调整分组；存在此类文件的分组不能删除，均返回 `409 Conflict`。

文件列表与批量详情会即时检查 `hive.downloadDir` 中当前返回记录的下载副本，并将副本大小和 `original_size` 比较。`localCheckStatus` 与 `localCheckObservedSize` 仅存在于响应中；大小变化的副本可通过重新上传接口覆盖 OSS，并以当前大小更新 `original_size` 基线。

## 预览契约

文件列表和批量详情返回 `previewable`、`previewUrl`、`thumbnailUrl`、`mimeType` 与 `previewBlockedReason`。`HiveRecordVOAssembler` 和主预览接口使用同一判定顺序：归档分类、`OSS_ONLY`、非 `UPLOADED` 状态、ZIP、超过 `hive.preview.max-size-bytes`、扩展名不受支持、缺少本地副本。阻断原因依次为 `ARCHIVE`、`OSS_ONLY`、`RECORD_NOT_READY`、`ZIP_ARCHIVE`、`TOO_LARGE`、`UNSUPPORTED_TYPE`、`LOCAL_MISSING`。

主预览入口是 `GET /categories/{category}/files/preview/{fileKey}`，只在判定允许时从受管下载目录流式返回内容，不再从 OSS 下载主文件。`LOCAL_MISSING` 和 `RECORD_NOT_READY` 返回 `409 Conflict`，`TOO_LARGE` 返回 `413 Payload Too Large`，其他拒绝原因返回 `415 Unsupported Media Type`。`thumbnailUrl` 指向独立的 OSS JPEG 缩略图入口，不暴露 OSS key、缩略图状态或图片尺寸等内部元数据。

## 关键配置项

- 无模块专属配置 key；依赖全局 `hive.downloadDir` 及 Spring 多部分上传限制（Controller 内 100MB）。

## 相关文件

- `src/main/java/cc/cc3c/hive/oss/controller/HiveFileController.java`
- `src/main/java/cc/cc3c/hive/oss/controller/HiveFileArchiveController.java`
- `src/main/java/cc/cc3c/hive/oss/controller/HiveFileDownloadController.java`
- `src/main/java/cc/cc3c/hive/oss/controller/vo/*.java`
- `src/main/java/cc/cc3c/hive/oss/config/HiveOssWebConfig.java`（CORS 等 Web 配置）

---

[返回模块总览](README.md)
