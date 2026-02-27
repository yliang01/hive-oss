# 服务编排模块

## 模块定位

在 Controller 与 OSS 适配层之间，负责上传、下载、远程同步的业务编排，以及按 `HiveRecordSource` 选择具体 OSS 实现（门面）；依赖领域仓储维护 `HiveRecord` 状态。

## 启动 / 触发入口

- **被动调用**：由 `HiveOssController` 在 HTTP 请求中调用，无独立进程或 main。
- **调度**：`HiveOssConfig` 开启 `@EnableScheduling`，供后续若有定时任务使用；当前同步与上传主要由 API 或目录监听触发。

## 关键依赖

- **内部**：`cc.cc3c.hive.oss.vendor.HiveOss`（由 `HiveOssService` 按 source 选择具体 Bean）、`HiveOssTask`、`HiveOssObject` 等 vendor 包类型。
- **外部**：`hive-domain`（`HiveRecord`、`HiveRecordRepository`、`HiveRecordSource`、`HiveRecordStatus`、`HiveDownloadStatus`）；Spring 容器、Commons IO（目录创建）、Commons Codec（MD5 生成 fileKey）。

## 上下游关系

- **上游**：后端 API 模块（Controller）、同步与监控模块（`HiveSyncMonitor` 调用 `HiveUploadService`）。
- **下游**：OSS 适配层（`HiveOssService.using(source)` 返回 `HiveOss`，进而调用 `HiveOssImpl`/具体 Client）、领域仓储（`HiveRecordRepository` 的查询与保存）。

## 核心组件

| 类 | 职责 |
|----|------|
| `HiveOssService` | 按 `HiveRecordSource` 返回对应 `HiveOss` Bean（如 `alibabaOss`、`tencentOss`），供上传/下载/同步/删除使用 |
| `HiveUploadService` | 上传目录初始化；同步上传 `uploadSync`；实现 `FileAlterationListener`，在目录监控中处理新文件上传并写库 |
| `HiveDownloadService` | 本地下载目录初始化；创建下载任务并异步执行，更新 `HiveRecord` 的下载状态；提供本地文件路径与任务进度查询 |
| `HiveSyncService` | 按 `HiveRecordSource` 拉取 OSS 对象列表，与 DB 中的 `HiveRecord` 比对，更新状态（如 OSS_ONLY、DB_ONLY、匹配数等）并返回统计 VO |

## 关键配置项

- `hive.uploadDir`：上传目录根路径（含各 source 子目录）。
- `hive.downloadDir`：下载文件落盘目录。

## 相关文件

- `src/main/java/cc/cc3c/hive/oss/service/HiveOssService.java`
- `src/main/java/cc/cc3c/hive/oss/service/HiveUploadService.java`
- `src/main/java/cc/cc3c/hive/oss/service/HiveDownloadService.java`
- `src/main/java/cc/cc3c/hive/oss/service/HiveSyncService.java`
- `src/main/java/cc/cc3c/hive/oss/config/HiveOssConfig.java`
- `src/main/java/cc/cc3c/hive/oss/config/HiveOssWebConfig.java`

---

[返回模块总览](README.md)
