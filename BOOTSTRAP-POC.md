# 管理启动与发布 POC

## 手动验证入口

从 IDEA 运行 cc.cc3c.hive.oss.setup.HiveOssApplication，或在项目根目录运行 package-release.cmd 后启动组装目录中的 start.cmd。首次访问 `http://localhost:8080/#/setup`。

程序先启动管理 UI，不解锁安全配置，也不初始化数据库、业务 OSS、发布服务或后台任务。业务未运行时，首页、文件页面和发布页面自动转到配置页；业务及发布 API 返回 503。现有 secret 文件不迁移、不修改。

主 Context 由 `cc.cc3c.hive.oss.setup.HiveOssApplication` 启动，默认只扫描 setup 包，负责静态 UI、/setup 和唯一的 HTTP 服务。解锁后显式注册 `cc.cc3c.hive.oss.runtime.BusinessContextConfiguration`，创建独立 WebApplicationContext；它只扫描 `cc.cc3c.hive.oss.business`，依赖 JAR 的自动配置仍由 Spring Boot 加载。

业务 Context 自己管理 Controller、异常处理器和参数解析器，通过独立 DispatcherServlet 处理 /api/*，不再向管理 Context 动态注册 Controller。BusinessServlet 在服务器启动时注册；解锁前直接返回 503，业务容器与 DispatcherServlet 初始化完成后才转发请求。两个 Context 没有父子关系，发布服务只读取 SecretConfigurationSnapshot 提供的原始 YAML 副本，不依赖管理侧 BusinessContextManager。

包职责：setup 仅属于管理容器；runtime 的业务 Context 配置仅由管理器显式注册；business 下的 controller、service、web、vendor、db、release 和业务 config 仅属于业务容器；`SecretYaml` 留在 setup，快照接口属于 business.config。

业务接口统一加 /api 前缀，例如 /api/categories、/api/db-backup、/api/self-release；Controller 内部路径不变。前端 API 客户端和预览 URL 已同步，Vite 代理 /setup 与 /api。没有旧接口地址兼容层。

秘密配置统一为标准 secret.yml。配置页可选择本地 YAML 文件让 Spring 直接读取；启动时不再从 application.yml 指定的磁盘路径自动读取明文配置。新机器使用 ticket 下载 secret.yml.enc，再输入密码解密为 YAML 并启动业务。

## 开发机发布

1. 修改代码及 hive-oss/src/distribution 中的资源，运行 package-release.cmd（跳过测试）。
2. CMD 构建 JAR，再直接重建 .release/build，不保留旧 build。
3. 先在配置页加载秘密 YAML 或解锁配置，启动业务后打开 `/#/admin/release`，填写版本、明确选择安全配置来源和密码。
4. 发布来源可以是「当前配置」或导入明文 YAML，所有来源都在业务启动后使用；生成新包密码至少 12 字符。
5. 发布时直接加密 YAML 原始字节，不提取字段、不转 JSON、不添加默认值。当前配置来源也是最初加载的 YAML 原文。
6. 点击「发布到 OSS」并确认，一次请求完成打包、加密、生成 manifest 和上传。页面显示当前运行构建及发布过程日志。IDE 与待发布 JAR 可以不同。
7. 后台通过阿里云 CLI 使用发布身份访问 OSS，按顺序上传，不下载回读校验，最后写 manifest.json，作为发布完成标记，不删除或覆盖对象。
8. 填写已完成版本和有效秒数，签发并由浏览器下载 ticket；服务端全程在内存处理，不创建票据目录。密码另行提供给使用机器。

发布设置在 application.yml 的 hive.release 中指定 Bucket、角色、地域、会话参数和 CLI operator profile。工作和输入目录使用安装目录下 .release。只在发布或签发 ticket 时通过 CLI 调用 STS 获取角色凭据；没有凭据不妨碍管理或业务启动。不再自动导入 external-secret.yml；需要本机基础或发布设置覆盖时统一使用 config/application.yml。

## 新机器与再次发布

新机器先获得程序 ZIP，解压并运行 start.cmd 或 sh start.sh。配置页在内存中解析下载 ticket，直接下载 manifest.json 和 secret.yml.enc 到 .release/download/<UUID>/，不保存 ticket、不复制加密文件。解锁成功并启动业务后才更新 .release/download/current.txt，内容为加密配置文件的绝对路径。原有秘密文件不迁移、不修改；重启读取所选本地包并再次要求密码，断网可使用本地包。

只有 Git 源码仓库用于发布。项目根目录的 assemble-release.cmd 从 JAR 和 src/distribution 中的启动文件组装 .release/build；该组装脚本本身不进入发布包。准备本机 operator profile，先在配置页启动业务，再打开发布 UI 发布。

程序更新先解压到新目录，由你停止旧进程、保留数据及本机配置后启动新程序。POC 不自动替换运行中的 JAR、不迁移或回退数据库、不做配置热切换。

## 状态与保留行为

- 发布与配置统一使用程序当前工作目录下的 .release：build 存放待打包程序，upload 存放发布产物，download 存放下载或本地加密生成的配置。
- 下载直接写入 download/<UUID>/manifest.json 与 secret.yml.enc。下载的配置包按目录保留，供后续离线解锁使用。
- 发布文件位于 .release/upload；每次发布开始时清除旧文件，本次保留 hive-release.zip、secret.yml.enc、manifest.json，不写出明文 secret.yml。
- 任务状态与日志仅保存在内存，不保存解锁密码。
- 重启后重新发起发布。发布不检查或跳过云端已有对象；上传失败后，如果该版本已有对象，使用新版本号重新发布，原对象保留。
- 不要在发布打包进行时重新组装 `.release/build`。
- 业务初始化失败关闭未完成容器并停止 H2 TCP 服务，管理 UI 保持运行；已经执行的数据库迁移不会自动回滚。

## 第一版假设与待讨论

1. 加密配置采用 hive-encryption 的 AES-CBC 密文，密码同时作为 salt 和 IV 输入，不兼容旧 HIVEYML1 和 .hcfg；CBC 不提供认证，不能可靠检测密文篡改。下载通过 manifest 中的哈希校验配置，没有发行者签名，也不强制发布版本与当前程序版本相等。
2. 所有使用机器默认共享配置包中的业务身份和数据加密密钥；不实现每机身份或单机撤销。
3. 本机安全配置更新需要重启；不支持后台无人值守解锁。旧密码标准输入启动方式不再作为 POC 主入口。
4. 发布设置来自 JAR 内 application.yml，本机覆盖使用 config/application.yml；修改后重启。本版不在 UI 编辑长期凭据。
5. 管理接口访问保护沿用部署环境／统一访问层约定；不新增 Controller 地址检查。
6. 发布任务只保存在内存，不持久化、不在重启后恢复；历史工作目录仍保留。
7. 大量本地旧候选包和失败临时文件不自动清理。
8. 自动构建／组装使用 Windows CMD；Linux 可以运行 Java 程序和 UI，但再次发布时需自行准备输入目录，本版不增加 Linux 组装脚本。

本版仅做 Java 编译、前端类型检查和构建，不启动应用，不进行功能测试或 OSS 发布测试。

秘密 YAML 使用 Spring 的 YamlPropertySourceLoader，支持嵌套字段、列表、占位符及 spring.config.activate.on-profile 文档选择。活动 profile 来自基础配置；秘密 YAML 中的 spring.config.import 不会执行额外文件导入。
