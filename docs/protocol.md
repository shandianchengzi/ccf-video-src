# 协议核对记录

核对日期：2026-09-13。以下来自 CCF 当前页面与 JavaScript，并以匿名真实请求核对。接口可能变化。

| 来源 | 已核对信息 |
| --- | --- |
| https://dl.ccf.org.cn/resources/js/anhui/video/videoIndex.js | POST /video/getConditions、POST /video/getVideoList |
| https://dl.ccf.org.cn/resources/js/anhui/video/videoDetail.js | POST /video/findVideoById，参数 resId；权限判断与清晰度字段 |
| https://dl.ccf.org.cn/computerRevision/resources/js/anhui/public/index.js | loginV2 使用 /login?service=...；tipNew 的权限代码与购买覆盖规则 |
| https://github.com/FongMi/CatVodSpider/blob/main/app/src/main/java/com/github/catvod/crawler/Spider.java | CatVod Java Spider 生命周期与字符串 JSON 回调接口 |
| https://github.com/actions/configure-pages/blob/main/action.yml | Pages 首次启用所需权限，不属于默认 GITHUB_TOKEN 能力 |

列表参数必须齐全：`pageNum`、`pageSize`、`searchTerm`、`dataYear`、`seriesText`、`sortRule`。排序字段是 `date` / `view_count`。筛选响应使用 `dateYears`、`meetingSeries`，包括历史年份桶 `2021-2015`。系列值中存在尾部空格，应保留用于请求；历史视频 ID 的尾部空格需剔除。

列表响应 `data.data` 为记录数组，`data.count` 为总数。初次核对共 10083 条。ID 按字符串处理，避免 JavaScript 大整数精度丢失。索引仅发布 ID、标题、封面、发表日期、年份、浏览数、访问标签、系列和派生研究标签。

详情 `video_audio_address` 为对象，含 `gao_definition`、`biao_definition`、`di_definition`、`ori_definition` 等字段。已观察媒体主机 `resources.ccf.org.cn` 和 `dlresources.ccf.org.cn`。仅在权限通过后向设备播放器返回 CCF HTTPS 地址，不写入目录、Pages 或日志。

权限例：匿名免费视频 `isAccess=01`；匿名会员视频 `isAccess=-1_03`。原站可在拒绝完整播放时仍返回媒体地址，因此判断只依赖是否存在 URL 是错误的。完整播放允许已经核对的正向代码；`isBuy=01` 且 `isBought` 不是布尔 `true` 时必须拒绝。未知权限代码默认拒绝，保持可诊断并等待适配。

登录走官方页面，凭据不经 GitHub。原站详情接口持有 dl.ccf.org.cn Cookie；不向 Pages、CDN 或跨域重定向传送该 Cookie。WebView 不注入 JS 接口，不允许本地文件访问或不安全混合内容。
