# CCF 影视仓视频源

面向个人学术学习的 CCF 数字图书馆视频源，使用影视仓 / TVBox 的 **CatVod Java type 3** 接口。GitHub Actions 维护公开目录，GitHub Pages 托管订阅与原生 dex JAR。视频由 CCF 原站提供。

## 导入

部署完成后，在影视仓的「设置 → 配置地址 / 点播接口」填写：

```text
https://shandianchengzi.github.io/ccf-video-src/tvbox.json
```

- [视频导航页](https://shandianchengzi.github.io/ccf-video-src/)
- [Actions 运行状态与可下载产物](https://github.com/shandianchengzi/ccf-video-src/actions)
- [Pages 设置](https://github.com/shandianchengzi/ccf-video-src/settings/pages)

只有 Actions 的 **deploy** 任务成功后，上述 Pages 地址才可使用。首次启用需要仓库 Settings → Pages → Build and deployment → Source 选择 **GitHub Actions**。自动化可以部署已启用的 Pages，默认 `GITHUB_TOKEN` 无权首次启用 Pages。

## 功能

| 模块 | 行为 |
| --- | --- |
| CCF视频 | 实时请求原站，支持原站年份、系列、发表时间 / 浏览数排序与分页 |
| 视频查找 | 使用影视仓搜索入口；按原站视频标题查找，支持分页；原站不可用时回退到最近公开目录 |
| CCF账号 | 首页账号卡片或「CCF账号」分类，打开官方登录页；自动保存服务端轮换的 Cookie，失效后优先尝试官方 SSO 静默恢复；也可导入本人的数字图书馆 Cookie |
| 研究方向 | 对标题及原站系列做可解释的多标签匹配，同时保留年份 / 系列 / 排序筛选 |
| 播放 | 高清、标清、流畅、原画；每次播放重新检查账号权限和媒体地址 |
| 自动维护 | 每天北京时间 03:23 或手动运行时全量同步公开列表和系列；普通代码推送复用上次已发布目录，只重新构建和部署 |

研究方向：嵌入式与固件仿真、芯片与数字电路、AI应用与边缘智能、大模型与智能体、具身智能与机器人、复杂工程与系统方法、同态加密与隐私计算、量子计算与量子信息。

分类词表位于 [`config/topics.json`](config/topics.json)，一个视频可以同时属于多个方向。分类依据仅为公开标题和会议系列，并非全文语义分类；详情页与转写内容不会被批量采集。`matches` 字段保留每个分类的命中词，可在网页卡片悬停查看。

## 登录与权限

1. 在影视仓选择「CCF数字图书馆」，点「登录 / 配置CCF账号」。
2. 选择「打开 CCF 官方登录页」，在 **CCF 官网**输入账号和密码并完成验证。插件不获取密码。
3. 返回数字图书馆后点「完成」，关闭账号页，重新进入视频。
4. 如果电视 WebView 太旧或遥控器操作困难，使用账号窗口中的「导入本设备 Cookie」。输入本人已登录 `dl.ccf.org.cn` 的 Cookie 请求头值，不含 `Cookie:` 前缀。不要把 Cookie 发到仓库、Issue 或聊天中。

Cookie 保存在影视仓应用的设备私有存储中。接口响应里的 `Set-Cookie` 会自动合并回本地，并同步给官方登录 WebView；这样可以跟随服务端的会话轮换。检测到 HTTP 登录跳转、401 或明确的未登录响应后，本源会利用仍有效的 CCF Passport Cookie 尝试一次隐藏 WebView SSO 恢复，再重试原请求；如果统一登录也已过期，仍会要求用户重新登录。为避免错误循环，同一设备一分钟内最多尝试一次静默恢复。

「已配置」只表示有本地凭据，不表示会话仍有效。有效性及会员 / 参会 / 购买资格由 CCF 播放时确认。服务端规定的最大会话期限、改密失效、风控或验证码不能也不会绕过。清除本源凭据不会清除其他视频源的 Cookie；单点登录还可能存在独立会话，可在官网退出。

**未取得完整播放权限时，不返回完整媒体地址。** 原站试看在官方网页进行；本源不把完整视频链接当作试看链接。不会绕过会员、专享、购买限制，不调用第三方解析服务。

## 部署与维护

工作流 `.github/workflows/maintain.yml` 包含：

1. Python 目录测试、Java 权限与筛选契约测试。
2. 使用 JDK 17 和 Android SDK 35 编译，D8 生成最低 Android 5.0 / API 21 的 dex JAR。应用自身的系统要求可能更高。
3. 定时 / 手动运行时，按原站总条数翻页抓取列表，再遍历原站系列获取准确归属；普通代码推送会下载、校验并复用 Pages 中上次保存的 `catalog.json`，避免无意义的全量抓取。`config/topics.json` 或抓取器本身发生变化、历史目录缺失或校验失败时自动回退到全量抓取。
4. 仅将白名单元数据、dex JAR、订阅 JSON 和静态页面打包，生成 MD5 / SHA-256 校验。
5. 部署 Pages。全量抓取遇到重复页、异常空页、结构变化或目录较上次减少超过 10% 时停止，不覆盖已发布目录。

Actions **不需要 CCF 账号、密码或 Secrets**。使用最小权限：读取源代码，部署阶段只增加 Pages 写入和 OIDC。第三方 Actions 固定到已核对的提交 SHA。

若 Pages 尚未启用，构建和目录产物仍可从 Actions 下载。先完成 Pages 设置，再对失败运行选择 **Re-run failed jobs**，即可重试 deploy 而无需重复抓取目录。工作流超时或接口错误可直接查看相应任务日志，日志只记录进度和公开元数据。

公共仓库的定时工作流可能在长期没有仓库活动后被 GitHub 停用，见 [GitHub 定时事件说明](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule)。届时在 Actions 中重新启用。

## 本地开发

Python 部分只使用标准库：

```bash
python -m unittest discover -s tests -v
python scripts/catalog.py
```

Java 编译需要 JDK 17、`ANDROID_SDK_ROOT`（或 `ANDROID_HOME`）、`platforms;android-35` 和 `build-tools;35.0.0`：

```bash
bash scripts/test-java.sh
bash scripts/build.sh
python scripts/package_site.py
python -m http.server 8080 --directory dist
```

`src/stubs` 仅用于编译接口，**不打包**到 dex，避免覆盖影视仓自带的 `Spider`。运行时无外部 Java 依赖；JVM 测试使用的 `org.json` 不进入 Android 产物。

## 验收范围

自动化验证数据解析、分页、分类、访问权限判断、URL 域名边界、Java 编译与 dex 生成。**不等同于在用户实际影视仓版本上验证成功。** 仍需在目标设备导入、打开账号窗口、完成本人登录，并分别验证一个免费视频和有权限的会员视频。具体记录见 [`docs/verification.md`](docs/verification.md)。

账号窗口优先使用 Android 生命周期获取前台 Activity，部分旧影视仓版本需兼容回退；如果窗口未出现，切换页面后重试。系统 WebView、验证码及影视仓版本差异需要真实设备确认。

接口依据和实现边界见 [`docs/protocol.md`](docs/protocol.md)。本项目非 CCF 官方应用，不托管视频内容。
