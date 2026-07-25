# Android Remote Control MCP（中文说明）

> Alpha 推荐配置：先用 USB 完成本地验收，再按需启用公网连接。Clash Meta 全局模式已在一台 OriginOS 设备上通过真机回归，但不代表所有 VPN、手机或系统版本都兼容。AI 执行期间请让手机由 AI 临时独占，主人继续在电脑上聊天。锁屏密码、生物识别、支付、OTP、系统权限与任何敏感授权必须由主人亲自完成；主人接回手机前先暂停远程触控。

> 本仓库是 `danielealbano/android-remote-control-mcp` 的修改分支，继续保留上游 MIT 许可证与作者归属。
> 目标是让 ChatGPT 等远程 AI 在本人可随时暂停的前提下，看屏、触控、输入和读取使用时长；不提供远程 Shell、任意 ADB 或权限绕过。

## 日常使用流程

1. 在手机上打开 **Android Remote Control MCP**。
2. 确认“无障碍服务”显示为已开启；未开启时进入“设置 → 快捷与辅助/更多设置 → 无障碍 → 已下载的应用”开启本应用。
3. 在 Server 页面启动 MCP 服务，确认常驻通知已经出现。
4. 确认 Remote Access 已连接并显示 HTTPS 地址；固定地址应使用自己的 Cloudflare Named Tunnel，不能把随机 Quick Tunnel 当作固定地址。
5. 在 ChatGPT 中连接 `https://你的域名/mcp`，按手机上的号码匹配页面批准 OAuth 客户端。
6. 此后正常使用不需要电脑、USB、Codex 或 Termux。需要立即禁止远程触控时，使用手机 App 或常驻通知中的“暂停触控”。

首次安装、USB 调试、权限配置和本地验收才需要 Windows 电脑。公网连接必须在本地功能通过后再启用。

## USB＋AI 环境配置建议

推荐首次安装或排障时，用 USB 数据线连接手机与电脑、开启 USB 调试，并让可信的 AI 编程助手协助执行本仓库的 PowerShell 脚本。AI 可以帮助检查 ADB 授权、构建和安装 APK、核对无障碍/使用情况权限、建立临时端口映射、采集脱敏日志并运行无敏感内容的冒烟测试。

项目处于 Alpha 阶段，不提供针对不同手机厂商、系统版本、代理/VPN、驱动、Android SDK 或个人电脑环境的逐机配置售后。遇到环境问题时，请优先把报错、日志和本仓库文档交给 AI 助手自助排查；这不代表 AI 可以绕过 Android 安全边界。锁屏解锁、生物识别、账号登录、验证码、支付、系统授权弹窗和其他敏感确认始终必须由设备主人完成。不要把 Bearer Token、账号凭据或私密聊天内容提交到 Issue。

## 当前底座与范围

本项目基于 [`danielealbano/android-remote-control-mcp`](https://github.com/danielealbano/android-remote-control-mcp) 的手机端原生实现。MCP Server、Ktor HTTP 服务、无障碍节点读取、坐标手势、截图、中文输入、OAuth 2.1、Bearer Token、前台服务和 Cloudflare/ngrok 隧道均直接运行在 Android 设备中。

核心策略是“节点优先，截图和坐标兜底”：

- 普通原生页面优先读取文本、content description、resource id、节点边界和可操作属性。
- WebView、Canvas、自绘页面和小程序没有足够节点时，仍可读取截图并按实时屏幕尺寸执行坐标触控。
- 文字输入优先使用自然的 InputConnection；部分 Chrome/WebView 或厂商页面拿不到连接时，只对明确可编辑节点尝试 `ACTION_SET_TEXT`，并要求结构化回读与目标文本完全一致，否则明确失败并交给截图/视觉路线。工具结果会说明实际输入路线；备用路线不宣称与真人键盘输入不可区分。
- 不提供远程 Shell 或任意 ADB 命令执行工具。
- 不绕过锁屏、生物识别、系统权限确认或安全键盘。

使用时长通过手机端 `UsageStatsManager`/`UsageEvents` 读取，可用工具为
`android_get_usage_summary`、`android_get_app_usage` 和 `android_get_screen_time`，支持今天、昨天、最近 7 天和最长 31 天的自定义区间。它们返回的是 Android 系统事件推导的近似值，不把估算值表述为精确计时。

当前没有“禁止打开抖音”等自律锁应用功能。OriginOS 最近任务里的“锁定应用”仅用于防止本 MCP 服务被后台清理，操作路径见下文保活建议。

## Windows 一键脚本

脚本位于 `scripts/windows`：

- `Check-AndroidDevice.ps1`：检查 ADB 授权并读取型号、系统、OriginOS、CPU 和屏幕信息。
- `Build-NativeDeps.ps1`：首次构建 ngrok/cloudflared 原生依赖；`Build-Debug.ps1` 缺少依赖时会自动调用。
- `Build-Debug.ps1`：运行本分支相关回归测试并构建 GMS debug APK；`-FullTestSuite` 可运行上游全部 JVM 测试。
- `Install-Or-Update.ps1`：安装或覆盖更新 APK，并启动手机端 App。
- `Check-ServiceStatus.ps1`：检查安装包、前台服务、无障碍服务和端口转发。

PowerShell 示例：

```powershell
Set-Location '<仓库目录>'
.\scripts\windows\Check-AndroidDevice.ps1
.\scripts\windows\Build-Debug.ps1
.\scripts\windows\Install-Or-Update.ps1
.\scripts\windows\Check-ServiceStatus.ps1
```

## 首次权限配置

核心功能只需要下列特殊权限；Android 要求由本人在手机上确认：

1. 无障碍服务：用于读取当前页面、截图和执行节点/坐标手势。
2. 使用情况访问权限：用于应用前台时长、打开次数和屏幕交互时长。
3. 通知权限：用于显示不可隐藏的前台服务状态和快速暂停入口。
4. OriginOS 的“读取已安装应用列表”：仅在 vivo/iQOO 系统提供该额外权限时显示，用于完整列出和查询应用；标准 Android 设备不会显示这一行。

相机、麦克风、位置、通知读取和文件目录授权不是通用屏幕控制的必需权限；不使用相应工具时不要授予。

## OriginOS 6 保活建议

不同 OriginOS 小版本的文字可能略有差异。对本应用完成以下设置：

1. 设置 → 应用与权限 → 权限管理 → 自启动：允许本应用自启动。
2. 设置 → 电池 → 后台耗电管理：允许后台高耗电，关闭本应用的电池优化。
3. 先打开本应用，再进入最近任务页面，找到本应用卡片并轻轻向下拉；卡片标题右侧出现锁图标即为锁定成功。
4. 设置 → 通知与状态栏 → 应用通知管理：允许本应用通知和常驻通知。
5. 设置 → 快捷与辅助/更多设置 → 无障碍：开启本应用服务。
6. 设置 → 应用与权限 → 特殊应用权限 → 使用情况访问：允许本应用。
7. 本应用 → Settings → Permissions → Installed App List (OriginOS)：点 Grant 并允许读取已安装应用列表。

Android 和 OriginOS 仍可能在系统更新、长时间闲置或手动清理后停止第三方进程。前台服务、开机启动和隧道重连能降低概率，但不能绕过系统厂商的强制管理。

## 本地 MCP 连接

服务默认监听手机的 `127.0.0.1:8080`。USB 调试阶段使用：

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb forward tcp:8080 tcp:8080
```

MCP 端点是 `http://127.0.0.1:8080/mcp`。每次请求必须携带 App 中显示的 Bearer Token，或者完成 OAuth 连接。不要为了省事同时关闭 Bearer 和 OAuth。

## 公网 MCP 连接

先复制 `cloudflare-worker/wrangler.example.jsonc` 为 `cloudflare-worker/wrangler.jsonc`，
再填入自己的 Cloudflare Account ID 与 Named Tunnel ID。真实配置已被 `.gitignore` 排除。

- Cloudflare Quick Tunnel 会生成随机 `trycloudflare.com` 地址，只适合临时测试。
- 固定地址使用本人 Cloudflare 账号中的 Named Tunnel、受控域名和 Tunnel Token。
- Named Tunnel 的公开路由指向手机本地 `http://localhost:8080`，外部地址由 Cloudflare 提供 HTTPS。
- ChatGPT 连接使用完整的 `https://固定域名/mcp`。
- OAuth 客户端必须在手机端核对号码并批准；可在 App 中单独撤销或全部撤销。

启用公网连接前必须完成本地截图、节点、坐标触控、中文输入和暂停触控回归测试。

## 安全和隐私边界

- 默认保留 Bearer 与 OAuth 双重可用，公网访问不允许匿名模式。
- 日志不得写入完整 Token、Cookie、支付信息、密码、验证码或完整输入文本。
- 默认不保存截图；测试截图只在必要时短期保留，验收后清理。
- 不读取短信验证码，不尝试访问安全键盘内容，不自动确认支付或系统生物识别。
- 银行、支付和系统安全页面需要本人操作时，远程端只能停留等待。
- 文件、相机、麦克风、位置、通知和任意 Intent 能力应按需关闭；它们不是通用看屏与触控的必要条件。

## 上游英文文档

完整上游架构和原始工具说明仍保留在：

- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/MCP_TOOLS.md`
- `docs/PERMISSIONS.md`
- `docs/PROJECT.md`

本分支的审计结论、真机信息、测试记录、未完成项和已知问题见 `docs/ALPHA_AUDIT_zh-CN.md`。

## 相关项目

抖音、Windows 电脑和 Android 手机三个 MCP 项目的统一入口：[mcp-tools-link-hub](https://github.com/mei-shui-xing/mcp-tools-link-hub)。
