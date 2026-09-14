package com.github.catvod.spider;

import android.content.Context;
import android.text.Html;
import com.github.catvod.crawler.Spider;
import com.github.catvod.spider.ccf.Access;
import com.github.catvod.spider.ccf.Account;
import com.github.catvod.spider.ccf.Catalog;
import com.github.catvod.spider.ccf.Http;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Native CatVod/TVBox type-3 source; no third-party parsing service. */
public final class CCF extends Spider {
    private Account account;
    private String catalogUrl = "https://shandianchengzi.github.io/ccf-video-src/catalog.json";
    private volatile Catalog catalog;
    private long loadedAt;

    @Override public void init(Context context, String extend) throws Exception {
        if (account != null) account.close();
        account = new Account(context);
        if (extend != null && extend.trim().startsWith("{")) {
            JSONObject config = new JSONObject(extend);
            String url = config.optString("catalog", catalogUrl);
            if (!url.startsWith("https://") || new URL(url).getUserInfo() != null)
                throw new IllegalArgumentException("目录地址必须是 HTTPS");
            catalogUrl = url;
        }
    }

    private synchronized Catalog catalog() throws Exception {
        if (catalog == null || System.currentTimeMillis()-loadedAt > 60*60*1000L) {
            try {
                Catalog next = new Catalog(Http.read(catalogUrl, null, ""));
                catalog = next;
                loadedAt = System.currentTimeMillis();
            } catch (Exception e) { if (catalog == null) throw e; }
        }
        return catalog;
    }

    private JSONObject conditions() throws Exception {
        try { return Http.post("/video/getConditions", new HashMap<>(), ""); }
        catch (Exception e) { return catalog().conditions(); }
    }

    private static JSONObject type(String id, String name) throws Exception {
        return new JSONObject().put("type_id", id).put("type_name", name);
    }

    private JSONArray filters(JSONObject conditions) throws Exception {
        JSONArray result = new JSONArray();
        for (String[] pair : new String[][]{{"dataYear", "年份", "dateYears"}, {"seriesText", "系列", "meetingSeries"}}) {
            JSONArray values = new JSONArray().put(new JSONObject().put("n", "全部").put("v", ""));
            JSONArray data = conditions.optJSONArray(pair[2]);
            if (data != null) for (int i=0; i<data.length(); i++) {
                String value = data.getString(i);
                values.put(new JSONObject().put("n", value.trim()).put("v", value));
            }
            result.put(new JSONObject().put("key", pair[0]).put("name", pair[1]).put("value", values));
        }
        return result.put(new JSONObject().put("key", "sortRule").put("name", "排序规则").put("value",
            new JSONArray().put(new JSONObject().put("n", "发表时间").put("v", "date"))
                .put(new JSONObject().put("n", "浏览数").put("v", "view_count"))));
    }

    @Override public String homeContent(boolean filter) throws Exception {
        JSONArray classes = new JSONArray().put(type("all", "CCF视频")).put(type("account", "CCF账号"));
        JSONObject facets = new JSONObject();
        JSONArray options;
        try { options = filters(conditions()); } catch (Exception e) { options = new JSONArray(); }
        facets.put("all", options);
        try {
            JSONArray topics = catalog().topics();
            for (int i=0; i<topics.length(); i++) {
                JSONObject topic = topics.getJSONObject(i);
                String tid = "topic:" + topic.getString("id");
                classes.put(type(tid, topic.getString("name"))); facets.put(tid, options);
            }
        } catch (Exception e) { if (account != null) account.message("分类目录暂不可用；可继续使用 CCF视频和搜索。"); }
        return new JSONObject().put("class", classes).put("filters", facets).toString();
    }

    private JSONObject accountCard() throws Exception {
        return new JSONObject().put("vod_id", "ccf:account").put("vod_name", "登录 / 配置CCF账号")
            .put("vod_pic", "").put("vod_remarks", account.configured() ? "本设备已配置 · 播放时验证" : "点击打开官方登录");
    }

    @Override public String homeVideoContent() throws Exception {
        JSONArray list = new JSONArray().put(accountCard());
        try {
            JSONArray rows = live("", 1, new HashMap<>()).getJSONArray("list");
            for (int i=0; i<rows.length(); i++) list.put(rows.get(i));
        } catch (Exception e) { account.message("CCF 列表暂不可用，请稍后重试。"); }
        return new JSONObject().put("list", list).toString();
    }

    private static int pageNumber(String pg) {
        try { return Math.max(1, Math.min(1000000, Integer.parseInt(pg))); }
        catch (Exception ignored) { return 1; }
    }

    private JSONObject live(String search, int page, Map<String,String> filters) throws Exception {
        Map<String,String> form = new HashMap<>();
        form.put("pageNum", String.valueOf(page)); form.put("pageSize", String.valueOf(Catalog.PAGE_SIZE));
        form.put("searchTerm", search);
        form.put("dataYear", filters.containsKey("dataYear") ? filters.get("dataYear") : "");
        form.put("seriesText", filters.containsKey("seriesText") ? filters.get("seriesText") : "");
        form.put("sortRule", "view_count".equals(filters.get("sortRule")) ? "view_count" : "date");
        JSONObject data = Http.post("/video/getVideoList", form, "");
        JSONArray input = data.getJSONArray("data"), list = new JSONArray();
        for (int i=0; i<input.length(); i++) list.put(liveCard(input.getJSONObject(i)));
        return Catalog.page(list, page, data.getInt("count"));
    }

    private JSONObject liveCard(JSONObject v) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return new JSONObject().put("vod_id", v.getString("id").trim()).put("vod_name", plain(v.optString("title")))
            .put("vod_pic", cover(v.optString("cover"))).put("vod_remarks", v.optString("visible_tag") + " · " + format.format(new Date(v.optLong("date"))));
    }

    @Override public String categoryContent(String tid, String pg, boolean filter, HashMap<String,String> extend) throws Exception {
        int page = pageNumber(pg);
        Map<String,String> facets = extend == null ? new HashMap<>() : extend;
        if ("account".equals(tid)) return Catalog.page(page == 1 ? new JSONArray().put(accountCard()) : new JSONArray(), page, 1).toString();
        if (tid.startsWith("topic:")) return catalog().query(tid.substring(6), "", page, facets).toString();
        try { return live("", page, facets).toString(); }
        catch (Exception e) { account.message("使用最近同步的目录。"); return catalog().query("", "", page, facets).toString(); }
    }

    @Override public String searchContent(String key, boolean quick) throws Exception { return searchContent(key, quick, "1"); }
    @Override public String searchContent(String key, boolean quick, String pg) throws Exception {
        String query = key == null ? "" : key.trim();
        if (query.isEmpty()) return Catalog.page(new JSONArray(), pageNumber(pg), 0).toString();
        try { return live(query, pageNumber(pg), new HashMap<>()).toString(); }
        catch (Exception e) { account.message("使用最近同步的目录搜索。"); return catalog().query("", query, pageNumber(pg), new HashMap<>()).toString(); }
    }

    private JSONObject detail(String id) throws Exception {
        if (!id.matches("[0-9]{1,24}")) throw new IllegalArgumentException("无效视频编号");
        Map<String,String> form = new HashMap<>(); form.put("resId", id);
        try { return Http.post("/video/findVideoById", form, account); }
        catch (Http.AuthenticationException expired) {
            if (account.recoverSession()) return Http.post("/video/findVideoById", form, account);
            throw expired;
        }
    }

    private static String plain(String value) { return Html.fromHtml(value == null ? "" : value).toString().trim(); }
    private static String cover(String url) {
        if (url.startsWith("/upload/")) return Http.BASE + "/file_server" + url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return Http.BASE + url;
        return url.startsWith("https://") || url.startsWith("http://") ? url : "";
    }

    @Override public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return new JSONObject().put("list", new JSONArray()).toString();
        String id = ids.get(0);
        if ("ccf:account".equals(id)) {
            account.show();
            JSONObject card = accountCard().put("vod_content", "凭据仅保存在本设备。官方登录完成后，关闭窗口并重新打开视频。若遥控器不便输入，可连接键鼠或导入本人 Cookie。显示已配置不代表会话仍有效，视频播放时会验证权限。")
                .put("vod_play_from", "账号设置").put("vod_play_url", "打开账号设置$ccf:account");
            return new JSONObject().put("list", new JSONArray().put(card)).toString();
        }
        JSONObject data = detail(id);
        StringBuilder authors = new StringBuilder();
        JSONArray names = data.optJSONArray("author");
        if (names != null) for (int i=0; i<names.length(); i++) { if (i>0) authors.append(" / "); authors.append(names.optString(i)); }
        String access = Access.allowed(data) ? "当前可完整播放" : Access.reason(data);
        JSONObject vod = new JSONObject().put("vod_id", id).put("vod_name", plain(data.optString("title")))
            .put("vod_pic", cover(data.optString("cover"))).put("vod_actor", authors.toString())
            .put("vod_remarks", data.optString("video_audio_duration"))
            .put("vod_content", access + "\n" + plain(data.optString("summary")) + "\n来源：" + plain(data.optString("location")))
            .put("vod_play_from", "高清$$$标清$$$流畅$$$原画")
            .put("vod_play_url", "播放$"+id+":gao_definition$$$播放$"+id+":biao_definition$$$播放$"+id+":di_definition$$$播放$"+id+":ori_definition");
        return new JSONObject().put("list", new JSONArray().put(vod)).toString();
    }

    @Override public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if ("ccf:account".equals(id)) { account.show(); return new JSONObject().put("parse", 0).put("url", "").toString(); }
        try {
            String[] parts = id.split(":", 2);
            JSONObject data = detail(parts[0]); // Always refresh authority and media URL; no catalog media cache.
            String url = Access.media(data, parts.length > 1 ? parts[1] : "gao_definition");
            JSONObject headers = new JSONObject().put("User-Agent", Http.UA)
                .put("Referer", Http.BASE + "/video/videoDetail.html?id=" + parts[0]);
            // Do not forward the dl.ccf session to CDN hosts.
            if (new URL(url).getHost().equalsIgnoreCase("dl.ccf.org.cn") && !account.cookie().isEmpty()) headers.put("Cookie", account.cookie());
            return new JSONObject().put("parse", 0).put("jx", 0).put("url", url).put("header", headers).toString();
        } catch (Exception e) {
            String message = e instanceof IllegalStateException ? e.getMessage() : "播放请求失败，请检查网络并重新登录 CCF。";
            account.message(message);
            return new JSONObject().put("parse", 0).put("url", "").put("msg", message).toString();
        }
    }

    @Override public String action(String value) { if ("ccf:account".equals(value)) account.show(); return ""; }
    @Override public boolean isVideoFormat(String url) { return url != null && Access.ccfHost(url) && url.matches("(?i).+\\.(mp4|m3u8)(\\?.*)?"); }
    @Override public void destroy() { if (account != null) account.close(); }
}
