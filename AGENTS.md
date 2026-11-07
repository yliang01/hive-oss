# hive-oss Agent 开发指南

本文件记录在 `hive-oss` 中开发必须遵守的模块边界、行为约束和发布操作。需要功能、接口或前端页面等细节文档时，先查阅本模块的 `docs/` 目录。仓库的整体构建顺序见上级 `CLAUDE.md`；仓库不是单一根 Maven 项目，没有根 `pom.xml`，Maven 命令应指定模块 POM 或在模块目录执行。

## 模块与容器边界

- `setup` 属于管理容器，负责配置页面、`/setup` 接口及业务启动管理。主入口是 `cc.cc3c.hive.oss.setup.HiveOssApplication`。
- 管理容器默认不加载秘密配置，也不启动数据库、OSS 业务服务、发布服务或后台任务。秘密配置解锁后，`BusinessContextManager` 显式注册 `runtime.BusinessContextConfiguration` 并创建独立业务 Web 容器。
- `business` 下的 Controller、异常处理、参数解析、配置、数据库、服务、OSS 适配和发布代码只属于业务容器。不要把业务 Controller 动态注册进管理容器，也不要让业务容器依赖管理容器 Bean；业务侧需要已加载配置原文时使用 `SecretConfigurationSnapshot`。
- 两个容器共享 HTTP 端口，但分别管理 MVC 配置。管理接口位于 `/setup`；业务接口统一使用 `/api` 前缀。前端 API 代理和预览 URL 需与后端路径保持一致。
- 业务初始化失败时，关闭未完成的容器并停止 H2 TCP 服务，管理 UI 继续运行。数据库迁移不会自动回滚。业务配置更新需要重启程序。

## 代码与数据约束

- 所有文件路径及路径组件（文件名、分类码、分组码）均按可信输入处理。除非用户明确改变策略，不为 `hive-oss` 应用代码增加路径校验、清理、编码或路径遍历检查。
- 项目中文件名全局唯一，不增加同名冲突处理、消歧或重命名逻辑。
- 不添加分组分配审计功能、操作人字段或分配历史。
- 文件删除仅通过批量选择接口 `POST /api/categories/{category}/files:mark-delete`；不要添加单行删除 UI，也不要恢复已移除的单记录 DELETE 接口。
- 后台文件操作完成后的记录刷新使用 `GET /api/categories/{category}/files:batch-get?fileKeys=...`，不要恢复单条记录 GET 接口。
- 数据库由 Flyway 管理，JPA 使用 `ddl-auto=validate`。所有 schema 变更必须放入 `src/main/resources/db/migration/`。
- 前端文件组筛选中，`all` 与 `ungrouped` 是不同值；不要把空字符串和未分组语义合并。长时间文件任务须沿用 store cleanup token，避免路由切换后更新过期页面。
- `fileKey` URL 路径编码尚未调整；修改文件 API 或预览 URL 时避免引入不兼容变更。

## 发布与配置分发

- `hive-release` starter 负责打包指定目录、加密配置、上传产物和签发下载 ticket；`hive-oss` 提供界面与业务调用入口。安装包不包含秘密 YAML。
- 发布必须在业务配置解锁并启动后进行。发布目录来自当前工作目录下 `.release/build`，本地发布产物写入 `.release/upload`，配置下载写入 `.release/download`。IDE 工作目录会影响这些路径。
- 源码仓库根目录的 `package-release.cmd` 构建并调用 `assemble-release.cmd`；后者从 `hive-oss/target/hive-oss-1.0.jar` 和 `hive-oss/src/distribution/` 组装 `.release/build`。已有 JAR 时可单独运行组装脚本。组装会重建 build 目录；发布打包期间不要重组输入目录。
- 当前安装目录包含 `hive-oss.jar`、`start.cmd` 和 `start.sh`。启动脚本切换到安装目录后运行 Java；运行数据和本机覆盖配置分别保留在 `data/` 与 `config/`。升级前停止程序并备份数据，只替换程序 JAR 和启动脚本。
- 发布目录会原样递归打包。不要将凭据、秘密配置、业务数据或日志放入 `.release/build` 或 `src/distribution/`。
- 发布配置放在 JAR 的 `hive.release` 配置中，本机覆盖使用 `config/application.yml`。发布机需要阿里云 CLI（含 ossutil）、有效 operator profile 和 AssumeRole 权限。管理 UI 启动不依赖发布凭据；只有发布或签发 ticket 时才需要这些凭据。
- 发布对象写入 `releases/<version>/`，依次上传 `hive-release.zip`、`secret.yml.enc`、`manifest.json`；manifest 是完成标记。禁止覆盖；上传失败不回滚已上传对象或本地文件，需换新版本号重试。发布前 starter 会检查 OSS Bucket 版本控制状态。
- 新配置包密码至少 12 个字符。加密沿用 `hive-encryption` 的 AES-CBC 格式，密码同时作为密码、salt 和 IV 输入；该格式没有认证标签，不能可靠检测密文篡改。不要宣称它兼容旧 `HIVEYML1` 或 `.hcfg` 文件。
- 真实阿里云集成测试是 opt-in，必须由用户手动运行；不要自行发起真实 OSS 发布或修改已有秘密文件。
- 新安装可从发布页面签发 ticket，用户在 `/setup` 输入 ticket 下载加密配置；ticket 与配置密码须分开提供。业务配置只在启动时加载，变更后需重启程序。

## 前端开发

- 前端唯一源码位于 `../hive-oss-frontend/frontend-app/src/`，不要编辑 `hive-oss-frontend/target/` 下的生成文件。
- Vue/TypeScript 改动完成前运行 `npm run typecheck`；UI 或状态改动后手动检查首页、文件列表、上传/预览、下载/解冻轮询及分类/分组管理相关流程。
- 前端依赖全局视觉样式 `src/styles/cloud-ops.css`。页面样式应扩展该系统，不要从头覆盖 Element Plus。
- 本地开发服务器端口为 3000，Vite 将 `/setup` 和 `/api` 代理到 `http://localhost:8080`。生产构建写入 `hive-oss-frontend/target/generated-resources/static`。

## 常用位置

- 后端：`src/main/java/cc/cc3c/hive/oss/`
- 数据库迁移：`src/main/resources/db/migration/`
- 安装包资源：`src/distribution/`
- 模块说明：`docs/` 下的各主题 Markdown 文档
- 详细功能、接口与页面文档：`docs/`
- 前端源码：`../hive-oss-frontend/frontend-app/src/`
