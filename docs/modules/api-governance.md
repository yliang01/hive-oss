# hive-oss API 冗余治理清单

## 前后端映射矩阵

| 前端模块 | 前端调用路径 | 调用方式 | 后端 Handler | 结论 |
|---|---|---|---|---|
| `home.js` | `GET /categories` | fetch | `HiveOssController#getCategories` | 保留 |
| `home.js` | `POST /db-backup/backup` | fetch | `DbBackupController#backup` | 保留 |
| `home.js` | `GET /db-backup/ack/{batchId}` | fetch | `DbBackupController#getAck` | 保留 |
| `home.js` | `GET /db-backup/backups` | fetch | `DbBackupController#listBackups` | 保留 |
| `home.js` | `POST /db-backup/restore/{batchId}` | fetch | `DbBackupController#restore` | 保留 |
| `home.js` | `DELETE /db-backup/{batchId}` | fetch | `DbBackupController#deleteBackup` | 保留 |
| `files.js` | `GET /categories` | fetch | `HiveOssController#getCategories` | 保留 |
| `files.js` | `GET /categories/{category}/groups` | fetch | `HiveOssController#getGroups` | 保留 |
| `files.js` | `GET /categories/{category}/files` | fetch | `HiveOssController#getFiles` | 保留 |
| `files.js` | `GET /categories/{category}/files/search` | fetch | `HiveOssController#searchFiles` | 保留 |
| Vue `files.ts` | `GET /categories/{category}/files:batch-get?fileKeys=...` | axios | `HiveFileController#batchGetFiles` | 保留；操作后批量刷新记录状态 |
| `files.js` | `POST /categories/{category}/files/download-task/{fileKey}` | fetch | `HiveOssController#downloadTask` | 保留 |
| `files.js` | `GET /categories/{category}/files/download-task-status/{fileKey}` | fetch | `HiveOssController#downloadTaskStatus` | 保留；仅返回任务状态和进度 |
| `files.js` | `POST /categories/{category}/groups/{groupId}/files:assign` | fetch | `HiveOssController#assignFilesToGroup` | 保留（主接口） |
| `files.js` | `POST /categories/{category}/groups/{groupId}/files:move` | fetch | `HiveOssController#moveFilesToGroup` | 冗余，前端改为调用 `assign` |
| Vue `files.ts` | `POST /categories/{category}/files:mark-delete` | axios | `HiveFileController#bulkMarkDelete` | 保留；文件删除统一走选中后批量标记 |
| `files.js` | `POST /categories/{category}/files/confirm-delete` | fetch | `HiveOssController#syncLocal` | 保留 |
| `files.js` | `POST /categories/{category}/files/sync-remote` | fetch | `HiveOssController#syncRemote` | 保留 |
| `files.js` | `POST /categories/{category}/files/unfreeze/{fileKey}` | fetch | `HiveOssController#unfreezeFiles` | 保留 |
| `files.js` | `GET /categories/{category}/files/unfreeze-status/{fileKey}` | fetch | `HiveOssController#unfreezeState` | 保留 |
| `files.js` | `POST /categories/{category}/files/release-local/{fileKey}` | fetch | `HiveOssController#releaseLocal` | 保留 |
| `files.js` | `POST /categories/{category}/files/upload` | xhr | `HiveOssController#upload` | 保留 |
| `files.js` | `GET /categories/{category}/files/preview/{fileKey}` | 浏览器资源直链（`img/iframe src`） | `HiveOssController#previewFile` | 保留（间接使用） |
| `category-admin.js` | `GET /category-admin/categories` | fetch | `CategoryAdminController#categories` | 保留 |
| `category-admin.js` | `POST /category-admin/categories` | fetch | `CategoryAdminController#createCategory` | 保留 |
| `category-admin.js` | `PUT /category-admin/categories/{categoryCode}` | fetch | `CategoryAdminController#updateCategory` | 保留 |
| `category-admin.js` | `POST /category-admin/categories/{categoryCode}/disable` | fetch | `CategoryAdminController#disableCategory` | 保留 |
| `category-admin.js` | `DELETE /category-admin/categories/{categoryCode}` | fetch | `CategoryAdminController#deleteCategory` | 保留 |
| `category-admin.js` | `GET /category-admin/categories/{categoryCode}/groups` | fetch | `CategoryAdminController#groups` | 保留 |
| `category-admin.js` | `POST /category-admin/categories/{categoryCode}/groups` | fetch | `CategoryAdminController#createGroup` | 保留 |
| `category-admin.js` | `PUT /category-admin/categories/{categoryCode}/groups/{groupId}` | fetch | `CategoryAdminController#updateGroup` | 保留 |
| `category-admin.js` | `DELETE /category-admin/categories/{categoryCode}/groups/{groupId}` | fetch | `CategoryAdminController#deleteGroup` | 保留 |

## 冗余与下线候选

### 0) 已下线/不再恢复

- `DELETE /categories/{category}/files/{fileKey}`
  - 文件管理页不再提供单条记录删除入口；所有分类统一使用选中后批量标记删除。
  - 当前后端 active controller 中没有该 handler；不要为前端重新添加单文件删除 API。
- `GET /categories/{category}/files/{fileKey}`
  - 已由 `GET /categories/{category}/files:batch-get?fileKeys=...` 替代；前端状态刷新不再逐条查询。

### 1) 高优先级

- `POST /categories/{category}/groups/{groupId}/files:move`
  - 与 `files:assign` 行为一致，属于语义冗余。
  - 处理策略：前端统一调用 `files:assign`；后端保留 `files:move` 作为兼容别名并标记 deprecated。

### 1.1 废弃窗口

- `files:move` 进入废弃观察期（建议 2 个发布周期）：
  - **阶段 1（当前版本）**：保留可用，返回 `X-API-Deprecated` 响应头。
  - **阶段 2（下一版本）**：仅文档保留迁移说明，不再推荐任何调用方使用。
  - **阶段 3（观察期后）**：若无外部调用证据，删除后端别名路由。

### 2) 不应判定为死接口

- `GET /categories/{category}/files/preview/{fileKey}`
  - 前端不是通过 `fetch` 调用，而是通过 `previewUrl` 赋值给 `img/iframe src` 间接触发。
  - 结论：保留。

## 下载任务契约

- 下载状态接口 `GET /categories/{category}/files/download-task-status/{fileKey}`：
  - 返回任务状态和进度；
  - 不返回下载目录物理路径或文件存在状态；
  - 前端不再自动打开 `file://` URL。
- 文件列表接口中的 `localFileAvailable`：
  - 后端按下载目录实时计算；
  - 仅控制下载与释放操作，不包含物理路径。
- 创建下载任务 `POST /categories/{category}/files/download-task/{fileKey}`：
  - 服务端强制要求记录为 `UPLOADED`、归档对象已解冻、下载状态不是 `downloading` 且本地副本不存在；文件列表中的 `downloadable` 使用同一规则；
  - 冻结归档返回 `423 Locked`；其他资格不满足的情况返回 `409 Conflict` 和 `{ "error": "..." }`；
  - 若 DB 为 `downloading` 但内存任务已丢失，服务端先删除 `.part` 文件并标记为 `failed`，再在当前请求创建替代任务。

## 解冻任务契约

- 解冻与状态查询仅接受 `storageClass=ARCHIVE` 的分类，其他分类返回 `400 Bad Request`。
- `restorable=true` 表示 `unfreezeTime` 缺失或已过期，归档对象尚不可下载。
- 解冻接口依据 OSS 状态返回：`NOT_STARTED` 时发起恢复并返回 `202 Accepted`，`IN_PROGRESS` 返回 `202 Accepted`，`COMPLETED` 保存有效截止时间并返回 `200 OK`。
- `unfreezeStatus=IN_PROGRESS` 仅用于页面重载后恢复轮询；下载资格以有效期和 `restorable` 为准。

## 本轮执行结果

- 已完成：前后端 API 映射矩阵输出。
- 已完成：移除下载状态接口中的物理路径字段，保留任务状态和进度。
- 已完成：按文件系统状态控制本地副本释放和再次下载。
- 已完成：前端批量操作统一到 `files:assign`。
- 已完成：`files:move` 标记 deprecated 并纳入废弃窗口。

