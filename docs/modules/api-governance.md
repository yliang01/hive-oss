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
| `files.js` | `GET /categories/{category}/files/{fileKey}` | fetch | `HiveOssController#getFile` | 保留 |
| `files.js` | `POST /categories/{category}/files/download-task/{fileKey}` | fetch | `HiveOssController#downloadTask` | 保留 |
| `files.js` | `GET /categories/{category}/files/download-task-status/{fileKey}` | fetch | `HiveOssController#downloadTaskStatus` | 保留，需补齐 `downloadUrl` |
| `files.js` | `POST /categories/{category}/groups/{groupId}/files:assign` | fetch | `HiveOssController#assignFilesToGroup` | 保留（主接口） |
| `files.js` | `POST /categories/{category}/groups/{groupId}/files:move` | fetch | `HiveOssController#moveFilesToGroup` | 冗余，前端改为调用 `assign` |
| `files.js` | `DELETE /categories/{category}/files/{fileKey}` | fetch | `HiveOssController#deleteFiles` | 保留 |
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

## 契约修复项

- 下载状态接口 `GET /categories/{category}/files/download-task-status/{fileKey}`：
  - 前端会读取 `downloadUrl` 自动触发下载；
  - 后端 VO 目前缺少该字段。
  - 处理策略：后端返回 `downloadUrl`（成功状态且本地文件存在时提供）。

## 本轮执行结果

- 已完成：前后端 API 映射矩阵输出。
- 已完成：下载状态接口补齐 `downloadUrl` 契约字段。
- 已完成：前端批量操作统一到 `files:assign`。
- 已完成：`files:move` 标记 deprecated 并纳入废弃窗口。

