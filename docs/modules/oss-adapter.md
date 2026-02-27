# OSS 适配层模块

## 模块定位

对多云对象存储（阿里云 OSS、腾讯云 COS）做统一抽象，提供列表、上传（单次与分片）、下载、解冻、删除等能力，并封装加密流与任务模型（`HiveOssTask`），供服务编排层调用。

## 启动 / 触发入口

- **被动加载**：通过 `HiveOssConfiguration`（`@Configuration`）在应用启动时注册 `HiveOss` Bean（`alibabaOss`、`tencentOss`）及静态任务配置；无独立 main，由服务层通过 `HiveOssService.using(source)` 按需调用。

## 关键依赖

- **内部**：`HiveOssClient` 接口及实现（`AlibabaOssClient`、`TencentOssClient`）、`HiveOssImpl`、`HiveOssTask`、`HiveRestoreResult`/`HiveRestoreStatus`、`HiveOssObject`、`HiveOssPartUploadResult`、`DownloadProgressListener`。
- **外部**：`hive-encryption-starter`（`HiveEncryptionConfig`、加解密）；阿里云 `aliyun-sdk-oss`、腾讯云 `cos_api`；Reactor（分片上传的并行流）；Commons IO。

## 上下游关系

- **上游**：服务编排层（`HiveUploadService`、`HiveDownloadService`、`HiveSyncService` 及 Controller 内直接调用的 `HiveOss`）。
- **下游**：阿里云 OSS API、腾讯云 COS API；加密模块（`HiveOssTask` 内对 InputStream/OutputStream 的加解密包装）。

## 核心类型

| 类型 | 职责 |
|------|------|
| `HiveOss` | 统一接口：listObjects、upload/uploadSync、download、restore/restoreCheck、delete、doesObjectExist |
| `HiveOssImpl` | 通用实现：委托具体 `HiveOssClient`；分片上传使用 Reactor 并行流；下载/上传流与加解密通过 `HiveOssTask` 处理 |
| `HiveOssClient` | 厂商抽象：分片上传生命周期、putObject、getObject、listObject、delete、restore/restoreCheck 等 |
| `AlibabaOssClient` | 阿里云 OSS 实现（含归档解冻与状态解析） |
| `TencentOssClient` | 腾讯云 COS 实现（restore 相关可为未实现或占位） |
| `HiveOssTask` | 任务模型：bucket/key、流、加密、分片上传进度与 uploadedMap；根据 `HiveRecordSource` 与静态配置解析实际 bucket 名 |

## 关键配置项

- `hive.oss.alibaba.*`：阿里云 endpoint、accessKey、standardBucket、archiveBucket 等（见 `AlibabaOssConfig`）。
- `hive.oss.tencent.*`：腾讯云 secretId、secretKey、region、bucket（见 `TencentOssConfig`）。
- `hive.oss.part.size`：分片大小（MB）。
- `hive.oss.concurrency`：并发度（读/传分片）。
- `hive.encryption.*`：加密用盐与密码（由 `HiveOssConfiguration` 注入到 `HiveOssTask` 静态配置）。

## 相关文件

- `src/main/java/cc/cc3c/hive/oss/vendor/HiveOss.java`
- `src/main/java/cc/cc3c/hive/oss/vendor/HiveOssImpl.java`
- `src/main/java/cc/cc3c/hive/oss/vendor/HiveOssConfiguration.java`
- `src/main/java/cc/cc3c/hive/oss/vendor/DownloadProgressListener.java`
- `src/main/java/cc/cc3c/hive/oss/vendor/vo/HiveOssTask.java`
- `src/main/java/cc/cc3c/hive/oss/vendor/vo/HiveRestoreResult.java`
- `src/main/java/cc/cc3c/hive/oss/vendor/client/HiveOssClient.java`
- `src/main/java/cc/cc3c/hive/oss/vendor/client/alibaba/AlibabaOssClient.java`
- `src/main/java/cc/cc3c/hive/oss/vendor/client/alibaba/AlibabaOssConfig.java`
- `src/main/java/cc/cc3c/hive/oss/vendor/client/tencent/TencentOssClient.java`
- `src/main/java/cc/cc3c/hive/oss/vendor/client/tencent/TencentOssConfig.java`
- `src/main/java/cc/cc3c/hive/oss/vendor/client/vo/HiveOssObject.java`
- `src/main/java/cc/cc3c/hive/oss/vendor/client/vo/HiveOssPartUploadResult.java`

---

[返回模块总览](README.md)
