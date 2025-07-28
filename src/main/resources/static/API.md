# OSS 管理前端 API 接口文档

## 基础信息

- 基础URL: `http://localhost:8080`
- 内容类型: `application/json`

## 接口列表

### 1. 获取Bucket列表

```
GET /buckets
```

**响应格式：**

```json
[
  "bucket1",
  "bucket2",
  "bucket3"
]
```

### 2. 获取文件列表

```
GET /buckets/{bucket}/files
```

**请求参数：**

- `bucket`: 桶名称（路径参数）
- `page`: 页码，从0开始（查询参数）
- `pageSize`: 每页大小（查询参数）

**响应格式：**

```json
{
  "files": [
    {
      "fileKey": "example/file.txt",
      "fileName": "file.txt",
      "size": 1024,
      "zipped": false,
      "status": "NORMAL",
      "updateTime": "2024-01-01T12:00:00Z",
      "unfreezeTime": null,
      "restorable": false,
      "downloadable": true,
      "deletable": true,
      "localPath": "/local/path/file.txt",
      "localPathExists": true
    }
  ],
  "total": 100,
  "page": 0,
  "pageSize": 50
}
```

### 3. 搜索文件 ⭐ 新增

```
GET /buckets/{bucket}/files/search
```

**请求参数：**

- `bucket`: 桶名称（路径参数）
- `keyword`: 搜索关键词（查询参数）
- `page`: 页码，从0开始（查询参数，可选，默认0）
- `pageSize`: 每页大小（查询参数，可选，默认50）

**响应格式：**

```json
{
  "files": [
    {
      "fileKey": "example/file.txt",
      "fileName": "file.txt",
      "size": 1024,
      "zipped": false,
      "status": "NORMAL",
      "updateTime": "2024-01-01T12:00:00Z",
      "unfreezeTime": null,
      "restorable": false,
      "downloadable": true,
      "deletable": true,
      "localPath": "/local/path/file.txt",
      "localPathExists": true
    }
  ],
  "total": 100,
  "page": 0,
  "pageSize": 50
}
```

**说明：**
- 支持模糊搜索，匹配文件名包含关键词的文件
- 搜索不区分大小写
- 返回格式与文件列表接口保持一致
- 支持分页

### 4. 获取单个文件信息

```
GET /buckets/{bucket}/files/{fileKey}
```

**请求参数：**

- `bucket`: 桶名称（路径参数）
- `fileKey`: 文件Key（路径参数）

**响应格式：**

```json
{
  "fileKey": "example/file.txt",
  "fileName": "file.txt",
  "size": 1024,
  "zipped": false,
  "status": "NORMAL",
  "updateTime": "2024-01-01T12:00:00Z",
  "unfreezeTime": null,
  "restorable": false,
  "downloadable": true,
  "deletable": true,
  "localPath": "/local/path/file.txt",
  "localPathExists": true
}
```

**错误响应：**

- 404 Not Found: 文件或Bucket不存在

```json
{
  "error": "文件不存在",
  "code": "FILE_NOT_FOUND",
  "fileKey": "example/file.txt",
  "bucket": "example-bucket"
}
```

### 5. 删除文件

```
DELETE /buckets/{bucket}/files/{fileKey}
```

**请求参数：**

- `bucket`: 桶名称（路径参数）
- `fileKey`: 文件Key（路径参数）

**响应：**

- 成功: 200 OK
- 失败: 4xx/5xx 错误

### 6. 解冻文件

```
POST /buckets/{bucket}/files/unfreeze/{fileKey}
```

**请求参数：**

- `bucket`: 桶名称（路径参数）
- `fileKey`: 文件Key（路径参数）

**响应：**

- 200 OK: 解冻立即完成
- 202 Accepted: 解冻请求已接受，正在处理中
- 失败: 4xx/5xx 错误

### 7. 解冻状态管理

```
GET /buckets/{bucket}/files/unfreeze-status/{fileKey}
```

**请求参数：**

- `bucket`: 桶名称（路径参数）
- `fileKey`: 文件Key（路径参数）

**响应：**

- 200 OK: 解冻已完成
- 202 Accepted: 解冻仍在进行中
- 失败: 4xx/5xx 错误

### 7. 释放本地文件

```
POST /buckets/{bucket}/files/release-local/{fileKey}
```

**请求参数：**

- `bucket`: 桶名称（路径参数）
- `fileKey`: 文件Key（路径参数）

**响应：**

- 成功: 200 OK
- 失败: 4xx/5xx 错误

### 8. 确认删除文件

```
POST /files/confirm-delete/{bucket}
```

**请求参数：**

- `bucket`: 桶名称（路径参数）

**响应：**

- 成功: 200 OK
- 失败: 4xx/5xx 错误

### 9. 远程同步

```
POST /files/sync-remote/{bucket}
```

**请求参数：**

- `bucket`: 桶名称（路径参数）

**响应格式：**

```json
{
  "ossTotal": 100,
  "ossOnly": 5,
  "ossToDbMatched": 90,
  "ossToDbMismatched": 3,
  "dbToOssMismatched": 2
}
```

### 10. 创建下载任务

```
POST /buckets/{bucket}/files/download-task/{fileKey}
```

**请求参数：**

- `bucket`: 桶名称（路径参数）
- `fileKey`: 文件Key（路径参数）

**响应：**

- 成功: 200 OK
- 失败: 4xx/5xx 错误

### 11. 查询下载任务状态

```
GET /buckets/{bucket}/files/download-task-status/{fileKey}
```

**请求参数：**

- `bucket`: 桶名称（路径参数）
- `fileKey`: 文件Key（路径参数）

**响应格式：**

```json
{
  "status": "downloading",
  "progress": 50,
  "downloadUrl": "http://example.com/download/file.txt"
}
```

### 12. 下载文件

```
GET /files/download/{fileKey}
```

**请求参数：**

- `fileKey`: 文件Key（路径参数）

**响应：**

- 成功: 文件流
- 失败: 4xx/5xx 错误

### 13. 上传文件 ⭐ 新增

```
POST /buckets/{bucket}/files/upload
```

**请求参数：**

- `bucket`: 桶名称（路径参数）
- `file`: 文件（multipart/form-data）

**响应格式：**

成功时（200 OK）：
```json
{
  "fileKey": "example/file.txt"
}
```

失败时（4xx/5xx）：
- 400 Bad Request: 文件格式不支持或文件过大
- 409 Conflict: 文件已存在
- 413 Payload Too Large: 文件大小超限

**错误响应：**

- 400 Bad Request: 文件格式不支持或文件过大
- 409 Conflict: 文件已存在
- 413 Payload Too Large: 文件大小超限

## 文件状态说明

- `NORMAL`: 正常状态
- `DB_ONLY`: 仅数据库中存在
- `TO_BE_DELETED`: 待删除状态
- `UPLOADED`: 已上传状态

## 错误响应格式

```json
{
  "error": "错误描述信息",
  "code": "ERROR_CODE"
}
``` 