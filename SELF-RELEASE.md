# 网页发布程序目录

Hive 启动后，先在配置页加载秘密 YAML 或解锁配置并启动业务，再打开「发布程序」，填写版本号和配置包密码，点击「发布到 OSS」并确认。程序自动打包当前已加载的配置、生成 manifest 并上传。

打包由 hive-release 的 ReleasePublisher 完成，打包源固定为程序当前工作目录下 `.release/build`。目录内容原样递归进入 ZIP，不自动补充文件或过滤数据。输出 ZIP 的顶层名称为源目录名。

## 准备发布目录

在项目根目录运行 `package-release.cmd`。脚本依次构建模块（跳过测试），然后调用 `assemble-release.cmd` 自动组装发布目录。JAR 来自 `hive-oss/target/hive-oss-1.0.jar`，其他发布资源统一维护在 `hive-oss/src/distribution/`。

组装结果：

```text
.release/build/
  hive-oss.jar
  start.cmd
  start.sh
  README.txt
```

程序当前工作目录下的 `.release/build`、`.release/upload` 和 `.release/download` 是固定目录。启动脚本会先切换到程序所在目录；IDE 启动时使用 IDE 配置的工作目录。目录内容原样打包，发布器不检查文件大小、后缀或 ZIP 内容，也不自动过滤、清理文件。

默认根目录为 ./.release；CMD 在项目根目录组装 .release/build，Java 在同一根目录运行时读取 build，并将本次上传文件写入 .release/upload。

已有 JAR 时可单独运行 `.\assemble-release.cmd`，只组装，不重新构建。脚本检查全部源文件后，直接重建 `.release/build`，不保留旧 build；如果复制失败，build 目录可能不完整。不要在发布打包期间重新组装 build。

发布目录只包含脚本明确列出的程序文件，不复制配置、凭据或业务数据。秘密配置通过 Setup 页面加载或解锁，不随程序目录分发。`.local/` 不纳入版本管理。Maven 不生成部署 ZIP，UI 不依赖当前运行 JAR，因此可以从 IDEA 启动 Hive 并打包已组装的目录。
管理 UI 先启动，业务配置按需加载。发布服务随业务 Context 启动，未启动业务时发布页面跳转到配置页。发布只使用当前已加载配置；生成新包需要至少 12 字符的密码。密码不保存到浏览器持久存储或日志。完整 POC 操作与假设见 [BOOTSTRAP-POC.md](BOOTSTRAP-POC.md)。

## 管理机器配置

必须显式配置发布 Bucket、角色和阿里云 CLI profile，完整说明见 [starter 配置说明](../hive-release/agent.md)。本机需安装阿里云 CLI 及其 ossutil 组件；缺项或 profile 无效会让对应发布操作失败，不影响管理 UI 启动；本地 `.release` 路径固定相对于程序当前工作目录。

发布目标和运行参数统一放在 JAR 内 application.yml 的 hive.release 字段；需要本机覆盖时使用 config/application.yml。实际 operator 凭据文件不进 JAR。已有秘密文件不修改，也不再自动加载；通过配置 UI 显式读取。starter 通过 Java ConfigurationProperties 绑定，不使用发布 JSON，不跨项目引用配置。

配置服务通过 Properties 注入本机设置，业务配置在解锁后注入独立业务容器并绑定为 Properties。发布服务从 SecretConfigurationSnapshot 获取当前已加载的秘密 YAML 原文；不依赖管理侧 BusinessContextManager，也不注入 Environment。只有业务容器启动边界负责构造 Spring 属性源。发布 HTTP 接口位于 /api/self-release，UI 路由仍为 /#/admin/release。

选中的本机 CLI operator profile 应允许通过 STS AssumeRole 扮演 HiveReleaseRole。Hive 调用 CLI，不读取 operator 凭据文件。业务运行 AccessKey 和 TerraformRole 不用于发布，发布配置和 operator profile 不进入发布包。
发布接口的访问限制由部署环境或统一访问控制层保证，self-release Controller 不检查客户端地址、Host、Origin 或专用操作头。

确认发布后，后台依次打包目录、加密 YAML、生成 manifest，并使用发布角色上传。如果该版本已有云端对象，不会跳过或覆盖，需要换新版本号重新发布。

## 发布结果

每个版本写入 `releases/<版本>/`，包含 hive-release.zip、secret.yml.enc、manifest.json。禁止覆盖，按顺序上传，不做上传后的下载校验，最后写 manifest.json，作为发布完成标记。失败保留对象和本地快照，没有删除、回滚或 Terraform 操作。

本地发布文件统一保存在程序当前工作目录下 `.release/upload/`，包含 hive-release.zip、secret.yml.enc、manifest.json。每次发布开始时清除旧的本地发布文件；原始秘密 YAML 只在内存中用于加密，不写入磁盘。OSS 上传 ZIP、加密配置和 manifest。后台一次处理一个任务，状态与日志仅保存在内存；重启后重新发布，不恢复旧任务。

UI 支持配置包下载／解锁、明文 YAML 加载、业务启动、发布及下载 ticket 签发。程序 ZIP 先手动下载解压，配置页可通过 ticket 获取安全配置。配置更新通过重启完成，程序升级仍由用户手动处理。

签发 ticket 时，服务端在内存读取 OSS manifest 并生成票据，直接作为 HTTP 下载响应返回；不创建本地目录或保存 manifest、ticket 文件。
