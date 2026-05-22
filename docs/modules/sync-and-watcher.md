# 同步与监控模块

## 模块定位

负责「远程同步」的触发与衔接：远程同步由 API 触发，在服务编排层执行。

## 触发入口

- **远程同步**：由 Controller 调用 `HiveSyncService.syncRemote(category)` 触发，按 category 解析 `bucket_name` 后同步，无定时或独立入口；若需定时同步可基于 `@EnableScheduling` 在服务层扩展。

## 行为说明

远程同步将 OSS 对象列表与 DB 中的 `HiveRecord` 进行比对，更新状态（`OSS_ONLY`、`DB_ONLY`、`UPLOADED`）并返回统计 VO。详见 [service-orchestration.md](service-orchestration.md) 中 `HiveSyncService`。

---

[返回模块总览](README.md)
