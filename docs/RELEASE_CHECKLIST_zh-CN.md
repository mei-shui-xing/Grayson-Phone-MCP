# 公开发布检查清单

## 发布前

- 保留上游 MIT 许可证、作者归属和子模块来源。
- 确认 `git status` 不出现 `.tmp-*`、OAuth 页面、真实 `wrangler.jsonc`、Token、证书或签名文件。
- `vendor/cloudflared` 必须保持在仓库锁定的提交；Android DNS 修复由 `patches/cloudflared-android-dns.patch` 在构建时自动应用。
- 运行 `scripts/windows/Build-Debug.ps1`，确认定向回归测试与 GMS debug APK 构建成功。
- 首次公开版本建议标为 beta，并明确完整上游 JVM 测试在 Windows 上存在既有不稳定项。

## GitHub 仓库

- 将原上游远程命名为 `upstream`，自己的仓库命名为 `origin`。
- 不直接向上游仓库推送本分支。
- 开启 GitHub Actions 后，先观察 CI；不要在 CI 未通过时打正式 tag。
- Release 至少附带 APK、变更说明、已知限制和首次安装步骤。

## 真机冒烟验收

- 覆盖安装后确认无障碍、通知、Usage Access 与手机端 MCP 服务正常。
- 验证读取屏幕、节点点击、坐标滑动、中文输入、Home/Back。
- 验证“暂停远程触控”后只读仍可用，所有写操作被拒绝；本机恢复后重新可用。
- 验证固定公网入口健康检查、OAuth 批准、`initialize` 与 `tools/list`。
- 微信小程序仍需单独专项验收，不能用普通第三方 App 测试代替。

## 不能宣传成已完成的内容

- Android 使用时长是系统事件推导的近似值，不是精确秒表。
- 厂商系统仍可能清理后台进程，前台服务和自启动只能降低概率。
- 锁屏、生物识别、支付确认、安全键盘和系统授权必须由手机本人操作。
