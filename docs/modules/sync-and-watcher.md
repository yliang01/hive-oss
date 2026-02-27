# 同步与监控模块

## 模块定位

负责「上传目录监听」与「远程同步」的触发与衔接：应用就绪后启动对指定上传目录的监控，新文件落入时由上传服务上传并入库；远程同步由 API 触发，在服务编排层执行，本模块仅从入口与事件角度说明。

## 启动 / 触发入口

- **上传目录监听**：`cc.cc3c.hive.oss.sync.HiveSyncMonitor` 在 `@EventListener(ApplicationReadyEvent.class)` 中启动 `FileAlterationMonitor`，对 `HiveUploadService` 提供的两个目录（`alibabaStandardFolder`、`alibabaAchieveFolder`）进行监控，并注册 `HiveUploadService` 为 `FileAlterationListener`。
- **远程同步**：由 Controller 调用 `HiveSyncService.syncRemote(source)` 触发，无定时或独立入口；若需定时同步可基于 `@EnableScheduling` 在服务层扩展。

## 关键依赖

- **内部**：`HiveUploadService`（实现 `FileAlterationListener`，提供监控目录并处理 `onFileCreate`）。
- **外部**：Apache Commons IO `FileAlterationMonitor`、`FileAlterationObserver`；Spring 的 `ApplicationReadyEvent`。

## 上下游关系

- **上游**：Spring 容器（发布 `ApplicationReadyEvent`）；用户或外部进程向监控目录投放文件。
- **下游**：`HiveUploadService`（目录路径、文件创建回调）；上传完成后由 `HiveUploadService` 调用 OSS 适配层与 `HiveRecordRepository`。

## 行为说明

- 监控目录：与 `hive.uploadDir` 下 `ALIBABA_STANDARD`、`ALIBABA_ACHIEVE` 对应路径一致；过滤规则为排除 `.hive` 结尾文件。
- 新文件创建时：`HiveUploadService.onFileCreate` 被调用，生成 fileKey（MD5 文件名）、创建 `HiveRecord`、调用 `HiveOss.upload`，成功后更新状态并删除本地文件（若实现如此）。
- 远程同步：见 [service-orchestration.md](service-orchestration.md) 中 `HiveSyncService`；本模块不包含同步逻辑本身，仅负责「上传目录监控」的启动与监听器绑定。

## 关键配置项

- `hive.uploadDir`：上传根目录，服务层据此构造各 source 子目录供监控使用。

## 相关文件

- `src/main/java/cc/cc3c/hive/oss/sync/HiveSyncMonitor.java`
- 上传逻辑与目录定义：`src/main/java/cc/cc3c/hive/oss/service/HiveUploadService.java`

---

[返回模块总览](README.md)
