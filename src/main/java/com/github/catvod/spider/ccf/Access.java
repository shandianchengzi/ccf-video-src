package com.github.catvod.spider.ccf;

import org.json.JSONObject;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class Access {
    private static final Set<String> FULL = new HashSet<>(Arrays.asList(
        "01", "02", "03", "04", "05", "06", "07", "08", "09", "010", "011", "03_1", "05_1", "07_1"));

    private Access() {}

    public static boolean allowed(JSONObject detail) {
        // Match the official player's purchase override, and fail closed on new codes.
        boolean bought = Boolean.TRUE.equals(detail.opt("isBought"));
        if ("01".equals(detail.optString("isBuy")) && !bought) return false;
        return bought || FULL.contains(detail.optString("isAccess", ""));
    }

    public static String reason(JSONObject detail) {
        if ("01".equals(detail.optString("isBuy")) && !Boolean.TRUE.equals(detail.opt("isBought")))
            return "该视频需要购买，请在 CCF 官网确认访问资格。";
        if (detail.optString("isAccess").startsWith("-1_"))
            return "请先在“CCF账号”中登录，再返回播放。";
        return "当前账号没有完整播放权限，请在 CCF 官网确认会员、参会或资源权限。";
    }

    public static boolean ccfHost(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null &&
                host != null && (host.equalsIgnoreCase("ccf.org.cn") || host.toLowerCase(java.util.Locale.ROOT).endsWith(".ccf.org.cn"));
        } catch (Exception e) { return false; }
    }

    public static String media(JSONObject detail, String quality) throws Exception {
        if (!allowed(detail)) throw new IllegalStateException(reason(detail));
        JSONObject sources = detail.optJSONObject("video_audio_address");
        if (sources == null) throw new IllegalStateException("CCF 未提供可用的视频地址。");
        String[] keys = {quality, "gao_definition", "biao_definition", "di_definition", "ori_definition"};
        for (String key : keys) {
            if (!Arrays.asList("gao_definition", "biao_definition", "di_definition", "ori_definition").contains(key)) continue;
            String url = sources.optString(key, "").trim();
            if (url.startsWith("//")) url = "https:" + url;
            if (url.startsWith("http://") && url.substring(7).split("/", 2)[0].endsWith(".ccf.org.cn"))
                url = "https://" + url.substring(7);
            // The site serves CCF-owned media hosts. Do not pass account data to unrelated hosts.
            url = url.replace(" ", "%20");
            if (ccfHost(url)) return url;
        }
        throw new IllegalStateException("没有受支持的 CCF HTTPS 视频地址，请到官网查看。");
    }
}
