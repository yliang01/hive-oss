# 缩略图补全与加密一致性巡检

## 目标

- **历史缩略图补全**：对 IMAGE_PREVIEW 分类下已上传但无 READY 缩略图的记录（无 `hive_record_image_meta` 或 `thumb_status` 非 READY），下载原图、生成缩略图并加密上传，写入 `hive_record_image_meta`（见 008_image_meta_split.sql）。
- **加密一致性巡检**：抽样检查 IMAGE_PREVIEW 桶内对象是否均通过应用层加密路径写入（无明文对象），并报告异常。

## 缩略图补全任务

### 触发方式

- 建议：管理端提供「补全缩略图」入口（如 `POST /categories/IMAGE_PREVIEW/files/backfill-thumbnails`），或定时任务按批处理。
- 可选配置：`hive.thumbnail.backfill.batch-size`、`hive.thumbnail.backfill.enabled`。

### 流程

1. 查询 `hive_record`：`bucket_name` = IMAGE_PREVIEW 对应桶，`deleted = false`，且文件名为支持的图片扩展名；与 `hive_record_image_meta` 左关联，筛选无 meta 或 `thumb_status` 为 `NULL`/`FAILED`/`PENDING` 的记录（见 `ThumbnailBackfillService` 的 needBackfill 逻辑）。
2. 按 `id` 分页（如每批 50 条），逐条处理：
   - 用现有 `HiveDownloadService` 或等价逻辑将原图下载到临时流/文件（走解密）。
   - 调用 `ThumbnailGeneratorService.generateFromFile` 或 `generateFromStream` 生成缩略图。
   - 使用 `ThumbnailKeyHelper.thumbKey(fileKey)`、`thumbFileNameForEncryption(fileKey)`，通过 `HiveOssTask.withEncryption(thumbFileName)` 上传缩略图到同桶。
   - 创建或更新 `hive_record_image_meta`：`thumb_key`、`thumb_status=READY`、`image_width`、`image_height`；失败则设 `thumb_status=FAILED`，不阻塞其余记录。
3. 失败不阻断：单条异常记日志并继续下一批。

### 与加密的约束

- 补全时下载原图必须走解密（与预览一致）；上传缩略图必须 `withEncryption(thumbFileName)`，保证云端无明文。

## 加密一致性巡检

### 目的

发现未通过 `withEncryption` 上传的明文对象（如历史或误配置），便于治理。

### 方式（建议）

- 不直接读对象内容判断是否密文（易误判），而是：
  - 对 IMAGE_PREVIEW 桶做 `listObjects`，得到 OSS 侧 key 集合。
  - 与 DB 中 `hive_record`（同桶、未删除）的 `file_key` 及 `hive_record_image_meta.thumb_key` 做比对：凡 DB 中存在的 key 均应为应用上传（当前实现下均为加密）；若存在 DB 中既非任何记录的 `file_key` 也非任何 image_meta 的 `thumb_key` 的 key，可标记为「未纳入管理的对象」，再人工确认是否为明文或遗留。
- 可选：对 DB 中 status=UPLOADED 的记录抽样下载解密，验证能正常解密（间接证明当时写入为加密）。

### 触发

- 管理端「加密一致性检查」按钮或定时任务，结果可写表或返回报告（如「共 N 条记录，M 个 OSS 对象，K 个未在 DB 中」）。

## 运维开关

- **缩略图生成开关**：可在配置中增加 `hive.thumbnail.generate-on-upload`（默认 true）。为 false 时，上传后不生成缩略图（原图仍加密上传），避免高负载环境临时关闭缩略图生成；补全任务可独立于该开关运行。

## 相关代码

- 上传后缩略图：`HiveUploadService.uploadThumbnailAndSaveMeta`（写 `hive_record_image_meta`）、`ThumbnailGeneratorService`、`ThumbnailKeyHelper`。
- 解密下载：`HiveDownloadService.streamPreview` / `streamPreviewThumbnail`，均使用 `withEncryption`。
- 预览策略与 OSS_ONLY：`HiveOssController.evaluatePreview` 对 OSS_ONLY 标记为不可预览，避免误走明文流。
