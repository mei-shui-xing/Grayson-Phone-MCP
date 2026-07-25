# Android Remote Control MCP：底座审计与验收记录

更新时间：2026-07-23

## 结论

选用 `danielealbano/android-remote-control-mcp` 作为底座是合适的。它已经把 MCP Streamable HTTP 服务、无障碍节点树、截图、坐标手势、WebView 合并、InputConnection 中文输入、Bearer/OAuth、前台服务以及 Cloudflare/ngrok 隧道放在手机端运行，最接近“以后脱离电脑独立工作”的目标。

本分支不加入远程 Shell、任意 ADB 命令或权限绕过。锁屏、生物识别、系统授权、安全键盘和支付确认继续由手机本人操作。

## 已核验环境和真机

- Windows 11；PowerShell 原生脚本。
- Android SDK：`C:\Users\33503\AppData\Local\Android\Sdk`。
- 平台与构建工具：Android 36 / Build Tools 36.0.0。
- ADB、JDK 21、Gradle Wrapper 8.14.4、Go 1.25.6、Rust、Maven 3.9.16、Android NDK 28.2 已可用。
- 真机：vivo V2536A（PD2536），OriginOS 6，Android 16 / API 36，arm64-v8a。
- 屏幕：1260 × 2750，密度 560 dpi。
- 首次读取时电量 40%，USB 充电；ADB 授权曾成功。

## 上游能力审计

上游现有 56 个 MCP 工具，已覆盖以下核心路径：

- `get_screen_state`：多窗口无障碍树、节点属性、可选标注截图和快照缓存。
- 节点操作：查找、点击、长按、滚动到节点。
- 坐标操作：单击、双击、长按、滑动、滚动、缩放和自定义多路径手势。
- 中文输入：Accessibility InputConnection，按字符提交并带有结果验证和自适应重试。
- 应用：列出、打开和关闭后台应用。
- 认证：Bearer Token、设备端 OAuth 2.1 批准与客户端撤销。
- 运行：前台服务、开机启动、Cloudflare Quick/Named Tunnel、ngrok。

## 本分支改动

1. 增加本地“暂停远程触控”安全闸：
   - 常驻通知显示当前触控状态，并提供暂停/恢复按钮。
   - 暂停状态跨进程重启保存。
   - 暂停后继续允许只读看屏，但阻止节点动作、坐标手势、系统导航和 InputConnection 文本输入。
2. 增加 Usage Access：
   - `get_usage_summary`
   - `get_app_usage`
   - `get_screen_time`
   - 支持今天、昨天、最近 7 天和最长 31 天的自定义区间。
   - App 权限页增加“App Usage & Screen Time”入口；系统权限必须本人手动授予。
3. 增强应用信息：
   - `list_apps` 增加首次安装时间、最后更新时间和是否可启动。
   - 新增 `get_app_info` 精确查询包名。
   - 真机发现 OriginOS 6 在 `QUERY_ALL_PACKAGES` 之外还要求
     `com.android.permission.GET_INSTALLED_APPS`；已增加按 ROM 探测的权限行和运行时申请，
     非 vivo/iQOO 设备不会显示这一厂商权限。
4. 修复导出组件边界：
   - ADB 配置 Receiver 与启动 Activity 增加 `android.permission.DUMP`，普通第三方 App 不能再显式调用。
   - E2E 配置 Receiver 从主 Manifest 移到 debug Manifest；测试 Receiver 同样受 DUMP 保护。
5. 增加 Windows 一键脚本：环境检查、原生依赖构建、测试/构建、安装更新和状态检查。

## 构建记录

- 上游原始构建第一次失败并非 Kotlin 源码问题，而是仓库要求先生成 vendor/ngrok-java 的两个 Maven JAR。
- 已从官方发布包安装并校验 Maven 3.9.16 SHA-512；已安装 NDK 28.2 和 Rust Android arm64 target。
- ngrok arm64 JNI 与 cloudflared arm64 二进制均已从子模块源码编译。
- `:app:compileGmsDebugKotlin`：通过。
- `:app:assembleGmsDebug`：通过。
- 本分支相关的 AppManager、MCP App tools、ActionExecutor、MainViewModel 和 ToolPermissions 回归测试：通过。
- 上游完整 JVM 测试在 Windows 上仍有既存不稳定项：DataStore 测试复用未结束的后台 scope 导致临时文件锁，部分 Ktor/OAuth 与 Cloudflare 协程测试超时；它们与本分支相关的定向测试分开记录，不能被表述为全套通过。
- APK：`app/build/outputs/apk/gms/debug/app-gms-debug.apk`。
- Gradle 的 native strip 对 Go/Rust 与若干第三方 `.so` 给出“无法剥离、原样打包”提示；这不是构建失败。

## 真机验收结果

APK 已在 vivo V2536A 真机覆盖安装；无障碍、通知权限和本地 MCP 服务已经跑通。
已通过真实 Streamable HTTP MCP 会话完成初始化、列出 62 个工具、读取当前屏幕、
返回桌面、打开应用和坐标点击。其余结果如下：

- 本人已在系统界面授予 OriginOS“读取已安装应用列表”和 Usage Access；没有通过 ADB 绕过授权。
- `list_apps(filter=user)` 已返回 110 个用户应用，并能按包名查询微信；`get_app_info` 返回安装时间、更新时间和可启动状态。
- `get_usage_summary`、`get_app_usage` 和 `get_screen_time` 已通过真实 MCP 会话返回结果。
- 节点点击、滑动、长按、双击、缩放、自定义轨迹和 Home/Back/Recents 系统导航均已执行成功。
- 普通文本框已通过 MCP 输入并核对完整中文 `中文输入测试`；清空文本导致节点快照失效后，重新读取节点再输入即可稳定成功。
- 常驻通知能够暂停远程触控；暂停时只读看屏仍成功，Home 等写操作被明确拒绝；点击通知中的恢复按钮后写操作重新成功。
- Server 主界面已增加 `Remote Touch` 状态和暂停/恢复按钮；真机覆盖安装后验证了界面状态切换、暂停时只读可用/写操作拒绝，以及恢复状态。
- 修正 Server 主界面的权限警告：只把无障碍、通知、Usage Access 和设备实际支持的已安装应用权限视为核心权限，不再要求相机、麦克风或通知读取等可选权限；真机确认授权齐全时警告已消失。
- 在用户同意后，以 DeepSeek 作为复杂第三方 App 验收对象：读取页面、节点点击“开启新对话”、坐标切换并还原“深度思考”、上下滑动、向真实输入框输入中文并发送；随后重新读取页面并确认出现“测试通过”回复。
- 真机读取屏幕使用时长成功：今天和最近 7 天均返回屏幕交互时长、解锁计数和 `Android UsageEvents; values are approximate` 计算说明。
- 调试结束前已从手机 Access 页面重新生成 Bearer Token，并重启 MCP 服务；健康检查恢复为 `healthy`。
- OriginOS 6 保活实机设置已完成：后台耗电改为“允许后台耗电”，自启动开关已开启，最近任务中的本应用卡片已向下拉并确认出现锁图标；完成后 MCP 健康检查仍为 `healthy`。

尚待本人参与的验收：

1. 微信普通小程序的专项验收尚未执行；DeepSeek 已覆盖复杂第三方页面、坐标兜底和真实中文发送链路，但不能伪装成微信小程序测试。
2. 固定 Cloudflare 公网地址已经配置完成；后续只需在 ChatGPT 中接入并完成一次手机端 OAuth 批准。

## 已知限制与后续建议

- 使用情况数据来自 Android UsageEvents，是系统事件推导的近似值；设备锁定时 Android 可能不返回数据。
- Quick Tunnel 地址仍是随机的；当前正式入口改用 Named Tunnel + Workers VPC，不依赖自有域名。
- 上游 Tunnel Provider 出错后缺少有退避的自动重连与网络切换监听，长期运行前建议补充。
- 上游 Bearer、Tunnel Token、ngrok token 和 OAuth/JWT 相关秘密目前由 Preferences DataStore 保存；公网阶段前建议迁移到 Android Keystore 支持的加密存储。
- `get_screen_state(include_screenshot=true)` 的截图数据可能显著占用模型上下文；默认应先读节点，只有节点不足时再取截图。
- 远程触控暂停仅保护 UI 操作与输入；文件、相机、位置、通知和任意 Intent 等非核心工具仍应在 MCP Tools 设置中按需关闭。
- “到期末前禁止打开抖音”一类自律锁尚未实现；后续如增加，应设计为本人在手机端主动开启、设置到期时间并保留紧急解除入口的可选专注模式，而不是让远程客户端任意锁定其他应用。

## 日常命令

```powershell
Set-Location '<仓库目录>'
.\scripts\windows\Check-AndroidDevice.ps1
.\scripts\windows\Build-Debug.ps1
.\scripts\windows\Install-Or-Update.ps1
.\scripts\windows\Check-ServiceStatus.ps1
```

## 2026-07-23 公网隧道补充验收

- 真机上的 Go `cloudflared` 进程会因 Android App 沙箱没有 `/etc/resolv.conf`，把系统 DNS 错误回退到 `[::1]:53`；这会导致 Quick Tunnel 创建成功后仍在边缘发现阶段退出。
- 本分支为 Android 原生 `cloudflared` 增加了 DNS-over-TLS 回退（优先阿里公共 DNS 的固定引导地址，随后回退 Cloudflare DNS），并让 SRV 目标地址解析走同一条回退链路。
- Quick Tunnel 在本机网络下固定使用 HTTP/2；Named Tunnel 为兼容 Workers VPC 使用 QUIC。
- 重新编译原生 arm64 二进制与 GMS debug APK 后，真机 Quick Tunnel 已完成边缘注册。手机从 5G 切回 Wi-Fi 时连接短暂关闭后自动重新注册，`cloudflared` 进程保持运行。
- 公网验收结果：`/health` 返回 200，OAuth 授权服务器元数据和受保护资源元数据均返回 200，未携带凭据访问 `/mcp` 返回 401。
- 已通过该公网地址完成一次真实 OAuth 2.1 动态客户端注册、手机端批准、授权码换取 Token、MCP `initialize` 和 `tools/list`；远程会话返回 62 个工具。测试 Token 未输出或落盘，两个临时验收客户端随后已在手机端撤销，Connected clients 恢复为空。
- `go test ./edgediscovery/allregions`、`ktlintCheck` 与 `:app:assembleGmsDebug` 通过。此前 Cloudflare Provider 的 Windows JVM 协程超时仍按既有不稳定项记录，不能表述为完整 JVM 测试全通过。
- 已创建 Named Tunnel，Android 连接为 Healthy；固定 Worker 入口已通过健康检查。
- Worker 通过绑定到该 Tunnel 的 Workers VPC Network，把请求转发到手机回环地址 `127.0.0.1:8080`。固定入口的 `/health`、OAuth 两类元数据和未授权 `/mcp` 已分别验证为 200、200、401。
- ChatGPT 动态客户端注册使用 `https://chatgpt.com/connector/oauth/{id}` 形式的每连接器回调地址；OAuth 重定向策略已加入严格的 HTTPS 主机与路径校验，拒绝端口、查询、片段、用户信息和伪装域名。真机更新后已完成 ChatGPT 动态注册、匹配码批准和 OAuth 连接，插件页显示“已连接到 冰冰的手机”。
