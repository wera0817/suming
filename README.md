# 观象 · 数字术数书斋

中文本地试用版：Vue 3 + TypeScript + Vite，Java 21 + Spring Boot 3.5，MySQL 8.4。

提供梅花问卦、手相图解、面相文化解读，以及 API 配置、知识条目管理和脱敏调用日志。界面采用宣纸、宋体、黛绿与朱砂的书斋风格。AI 身份明确，不冒充真人。

## 后端分层

代码根目录为 `backend/src/main/java/cn/guanxiang`，采用 Spring MVC 的 Controller → Service → Mapper 分层：

```text
cn.guanxiang
  controller  HTTP 路由、参数校验、调用 Service、返回结果
  service     业务规则、报告生成、知识检索、鉴权、事务边界
  mapper      按表划分的数据访问，SQL 只允许出现在这一层
  dto         请求参数、配置和知识条目传输对象
  domain      梅花排盘算法与领域模型
  client      外部 AI HTTP/SSE 协议适配
  security    请求安全过滤器、加密工具
  common      JSON 编解码、业务异常、统一异常处理
```

例如：`ConsultationController` → `ConsultationService` / `ReportService` → `ReportMapper` / `MessageMapper`。后台管理入口为 `AdminController`，不直接调用 Mapper 或外部 AI 客户端。

Mapper 当前使用 Spring JDBC 的 `JdbcTemplate`，通过 `@Repository` 注册，**不是 MyBatis Mapper**。各 Mapper 的数据库连接均为私有字段，不向上层暴露。报告完成时，由独立 `ReportService.complete()` 的 `@Transactional` 统一提交追问、回答和报告状态；流式任务通过 Spring 注入的 Service 代理调用该事务方法。Controller 不编写 SQL、照片处理或 AI 测试业务逻辑。

分层约束见 `ArchitectureTest`；报告读写和删除竞态见 `ReportServiceTest`；事务代理的提交、回滚行为见 `ReportTransactionTest`。原有 URL、响应字段、数据库表和迁移不变。

## 启动

Windows 需要 Node.js 22.12+、JDK 21、MySQL Server 8.4 安装文件。脚本优先使用 JAVA_HOME 或用户目录 .jdks 下的 JDK；Maven 优先使用本机或缓存版本，否则从 Apache 下载并校验。

在项目目录的 PowerShell 执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/setup.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1
```

- 用户端：`http://127.0.0.1:9099`
- 后台：`http://127.0.0.1:9099/#/admin`
- 后端：`http://127.0.0.1:18080`
- 管理账号：`admin`。随机初始密码在本机 `.local/config.json` 的 `adminPassword` 字段。没有通用默认密码。
- 停止：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/stop.ps1`
- 已构建后快速启动：`scripts/start.ps1 -SkipBuild`
- 默认启用局域网试用：前端监听 `0.0.0.0:9099`，使用电脑 IPv4 地址和 9099 端口访问。后端与 MySQL 仍只绑定本机；不要配置公网端口映射。
- 仅本机模式：`scripts/start.ps1 -SkipBuild -Lan:$false`。
- 如防火墙阻止局域网访问，在管理员 PowerShell 中运行 `scripts/open-lan-firewall.ps1`。此脚本只管理本站专用规则，仅允许局域网访问 Node.js 的 TCP 9099，不关闭防火墙，不放行数据库或后端端口。
- 临时域名试用已精确允许 `yun.byca.cn`（不是所有主机或子域名）。网络启动模式下允许该域名的 HTTP/HTTPS 来源，端口限默认 80/443 或 9099；仅解除主机及来源校验，不代表已配置公网映射或 HTTPS。不要通过未加密的公网 HTTP 输入管理密码或敏感咨询；长期部署仍需构建产物和 HTTPS 反向代理。

启动脚本使用安装的 mysqld 程序，但创建独立实例、数据目录和项目账号，端口 13306，**不复用或更改电脑上已有的 MySQL 服务**。数据库为 `guanxiang`；自动化测试使用独立的 `guanxiang_test`。

`.local` 包含密钥、数据库和启动日志，已排除版本控制，并在首次配置时限制为当前 Windows 用户及 SYSTEM 可访问。不要提交、分享或随意删除。API 密钥依赖稳定的 masterKey 解密；备份应同时保护数据库与该密钥。初次启动后后台密码以 BCrypt 哈希存入数据库，后续仅修改 config.json 不会重置已建立的密码。

## 配置 AI

后台填写 Base URL（通常以 `/v1` 结尾）、API Key、服务名称、文本模型与视觉模型，保存后分别测试。接口使用 Chat Completions；模型 ID 由实际服务商提供，本项目不预设付费模型。

文本和视觉可共用连接或独立配置。密钥在服务端以 AES-256-GCM 加密，接口仅返回 hasKey 标记，不返回原密钥。密钥留空表示保留已保存值。非本机服务必须使用 HTTPS。

部分服务仅接受 `max_completion_tokens`，可在后台切换 Token 参数名。HTTP 连接、鉴权、限流、参数不兼容、超时、断流、输出截断及不明引用均有独立错误状态；不自动重试，不生成模拟报告。未配置 API 时仍能计算卦象。

每次初次解读和追问前，先用文本 API 单独审核咨询范围，仅接受 `ALLOW` 决定，再开始流式解读；拒绝或审核异常时不输出解读正文。审核最多使用 256 输出 Token 和 15 秒（不超过后台设置），会额外产生一次 API 调用。解读系统规则也限定为问卦、解卦与手相面相文化咨询，不提供编程、翻译等无关服务。职业背景提到程序开发，不等于索要代码，不应因此拒绝正常事业问卦。被拒绝的追问不会写入消息，原报告保持可用。真实模型的范围判断与抗绕过效果仍需实际服务验收，不能把模拟审核测试等同于已验证模型效果。

## 知识库

首次启动写入 68 条基础资料：64 条《周易》卦辞、2 条本站规则/术语说明、2 条本站手相/面相文化说明。古籍正文与本站整理分开标注；相书专论尚未完成版本核对，未声称建立了完整古籍相术库。此版不含爻辞全集、八字或自动全网采集。

处理流程：问题及程序盘面 → 中文双字关键词与标题匹配 → 最多 6 条启用且已核对的条目 → API 解读 → 核验 `[K:条目id]` 引用。盘面相关的本卦、互卦、变卦及规则优先检索。模型不能修改程序盘面。历史报告保存引用资料快照，不随后台编辑漂移。

后台支持新增、编辑、启停和 UTF-8 Markdown/TXT 导入。导入先本地预览，再填写术式、流派、出处、版本和权限说明；未核对的条目不得启用。导入限制 100 KB，正文最多 20000 字符。条目正文作为不可信资料，不能覆盖系统规则。

**现有“核验”只检查引用编号是否在本次资料集合内，不证明模型对资料的解释正确，也不能保证完全抵抗提示注入。** 重要古籍扩库需要逐条版本校勘和权限核查，不能把公开网页或现代译注一概当作公版。

## 图片与记录

仅接收真实 JPEG/PNG/WebP 文件，最大 10 MB、2400 万像素，最短边 200 像素。服务端通过文件内容识别格式，内存解码，最长边压到 1600 像素，重编码为无原始元数据的 JPEG。上传阈值高于单文件上限，ImageIO 磁盘缓存关闭，不创建照片档案。

首次咨询说明文字将发送给外部服务；每次图片咨询还需单独授权。视觉模型先返回结构化的清晰度、主体和中性观察；不满足要求则要求重拍。文字报告只保留观察，追问不再次访问原图。外部 API 服务商的照片和文字留存规则另行适用。

同一浏览器的记录使用随机 HttpOnly、SameSite=Strict Cookie 隔离，数据库保存凭证的 SHA-256 摘要。报告编号不等于访问权限。删除报告会级联删除追问；清理 Cookie 后无法找回原记录。设备/数据库管理员仍可读取本机文字数据。

其他浏览器即使拿到报告编号，也不能列出、读取、删除或追问该报告；没有公开咨询列表。这里的“仅自己看”指浏览器凭证隔离，不是真人身份认证：共用浏览器、复制凭证或设备被他人使用时，不能区分两个人。当前无账号，不支持跨设备找回；外部模型仍会处理本次发送给它的咨询。

手相和面相仅作文化术语解释，不从外貌、掌纹推断人格、诚信、能力、健康、寿命或命运。不提供生死、疾病、投资收益等确定性断言。

## 验证

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1
```

构建执行 Java 单元测试、Vue 类型检查与生产构建。浏览器测试使用真实 MySQL、独立测试后端 18180、测试前端 5174、模拟 API 19091；不污染使用端的配置和报告。截图位于 `test-results/screenshots`。测试脚本自动关闭测试后端和模拟服务。

验收覆盖排盘边界、密钥加密、图片字节验证、后台鉴权、Origin 校验、访客隔离、知识启停/导入、无匹配资料、引用编号、追问不换盘、异常 API 和手机/桌面布局。

模拟视觉响应只证明程序对 usable/subject/quality 的分支处理，不证明真实模型能识别模糊、多人、手掌或面部。没有真实 API 密钥时，真实模型文本质量、提示注入防护效果、视觉识别和实际服务商兼容性均为待验证项。

## 手动部署约定

`.env.example` 是变量说明，Spring Boot 不自动加载该文件。手动部署需将变量设置为进程环境，先创建数据库账号和 utf8mb4 数据库，Flyway 自动建表；前端构建产物在 `frontend/dist`。生产反向代理应同源代理 `/api`，开启 HTTPS，精确设置 ALLOWED_ORIGINS，并开启 SECURE_COOKIES。

当前启动方式是本地开发/试用方式，不是公网生产部署方案。暂无公众账号、额度计费、支付、备份轮转、公开域名及运维监控。限流与并发控制按单实例设计，后台测试使用少量付费 API 用量。

`127.0.0.1:9099` 只代表访问者自己的电脑，不能把此地址发给外网用户。当前默认启动模式允许同一局域网通过本机 IPv4 地址访问，不等于公网开放；还需确认本机防火墙允许局域网连接。公网试用应部署到指定服务器，通过域名和 HTTPS 提供构建后的前端与同源 `/api` 代理，精确配置来源与 Secure Cookie；不要直接把 Vite 开发端口、MySQL 或管理接口映射到公网。公开邀请试用前还需增加访客额度/整体费用上限，避免仅依赖可重新生成的访客凭证限流。
