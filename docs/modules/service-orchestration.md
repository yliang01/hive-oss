# 服务编排模块

## 模块定位

在 Controller 与 OSS 适配层之间，负责上传、下载、远程同步的业务编排；业务文件 bucket 由 category 映射提供，DB 备份走专用 `backupBucket`；依赖领域仓储维护 `HiveRecord` 状态。

## 启动 / 触发入口

- **被动调用**：由 `HiveOssController` 在 HTTP 请求中调用，无独立进程或 main。
- **调度**：`HiveOssConfig` 开启 `@EnableScheduling`，供后续若有定时任务使用；当前同步与上传主要由 API 或目录监听触发。

## 关键依赖

- **内部**：`cc.cc3c.hive.oss.vendor.HiveOss`（由 `HiveOssService` 选择具体 Bean）、`HiveOssTask`、`HiveOssObject` 等 vendor 包类型。
- **外部**：`hive-domain`（`HiveRecord`、`HiveRecordRepository`、`HiveStorageProvider`、`HiveRecordStatus`、`HiveDownloadStatus`）；Spring 容器、Commons IO（目录创建）、Commons Codec（MD5 生成 fileKey）。

## 上下游关系

- **上游**：后端 API 模块（Controller）。
- **下游**：OSS 适配层（由 `HiveOssService` 返回 `HiveOss`，进而调用 `HiveOssImpl`/具体 Client）、领域仓储（`HiveRecordRepository` 的查询与保存）。

## 核心组件

| 类 | 职责 |
|----|------|
| `HiveOssService` | 按 `HiveStorageProvider` 返回对应 `HiveOss` Bean（如 `alibabaOss`、`tencentOss`），供上传/下载/同步/删除使用 |
| `HiveUploadService` | 同步上传 `uploadSync` 与流式上传 `uploadStreaming`（bucket 由 category 决定）；写库并可选生成缩略图 |
| `HiveDownloadService` | 本地下载目录初始化；集中判断下载资格（`UPLOADED`、已解冻、非下载中、无副本）；按分类和分组落盘并异步执行下载，更新 `HiveRecord` 的下载状态；按文件系统判断本地副本、提供任务进度查询与本地副本释放 |
| `HiveLocalCheckService` | 在文件列表、批量刷新和重新上传前即时检查本地源文件；检查结果不写入数据库 |
| `HiveSyncService` | 按 category 解析 bucket 后拉取 OSS 对象列表，与 DB 中的 `HiveRecord` 比对，更新状态（如 OSS_ONLY、DB_ONLY、匹配数等）并返回统计 VO |

## 关键配置项

- `hive.downloadDir`：下载文件落盘根目录，内部按 `{categoryCode}/{groupCode 或 _ungrouped}` 分目录。
- `hive.localScanDir`：本地源文件检查根目录，内部按 `{categoryCode}/{groupCode 可选}` 分目录。

## 下载与解冻协作

- `HiveFileArchiveController` 负责向 OSS 发起或查询归档解冻，并将恢复状态和有效期写回 `HiveRecord`。
- `HiveDownloadService` 使用该有效期阻止冻结归档下载；下载 Controller 在启动前先修复没有内存任务的过期 `downloading` 状态，再按统一资格决定是否创建任务。
- 下载任务使用 `.part` 临时文件；成功后移动为最终副本，失败或过期修复时删除临时文件。

## 相关文件

- `src/main/java/cc/cc3c/hive/oss/service/HiveOssService.java`
- `src/main/java/cc/cc3c/hive/oss/service/HiveUploadService.java`
- `src/main/java/cc/cc3c/hive/oss/service/HiveDownloadService.java`
- `src/main/java/cc/cc3c/hive/oss/service/HiveSyncService.java`
- `src/main/java/cc/cc3c/hive/oss/config/HiveOssConfig.java`
- `src/main/java/cc/cc3c/hive/oss/config/HiveOssWebConfig.java`

---

[返回模块总览](README.md)
