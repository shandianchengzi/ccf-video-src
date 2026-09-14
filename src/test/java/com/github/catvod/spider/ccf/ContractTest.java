package com.github.catvod.spider.ccf;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Arrays;

public final class ContractTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        for (String code : new String[]{"01", "02", "03", "03_1", "06", "010", "011"})
            check(Access.allowed(new JSONObject().put("isAccess", code)), "allow " + code);
        for (String code : new String[]{"", "true", "0", "099", "-1_02", "-1_03", "-03", "-03_1", "-06_2"})
            check(!Access.allowed(new JSONObject().put("isAccess", code)), "deny " + code);
        JSONObject d = new JSONObject().put("isAccess", "01").put("isBuy", "01");
        check(!Access.allowed(d), "purchase overrides public access");
        check(Access.allowed(d.put("isBought", true)), "purchased");
        check(!Access.allowed(d.put("isBought", "true")), "do not coerce untrusted boolean");
        check(!Access.ccfHost("https://ccf.org.cn.evil.example/a.mp4"), "hostname suffix boundary");
        check(!Access.ccfHost("https://x@dl.ccf.org.cn/a.mp4"), "userinfo");
        check(!Access.ccfHost("http://dl.ccf.org.cn/a.mp4"), "HTTPS");
        JSONObject media = new JSONObject().put("isAccess", "01").put("video_audio_address",
            new JSONObject().put("gao_definition", "https://resources.ccf.org.cn/a b.mp4?auth_key=test")
                .put("ori_definition", "https://dlresources.ccf.org.cn/full.mp4"));
        check(Access.media(media, "gao_definition").contains("a%20b.mp4?auth_key=test"), "preserve query, encode spaces");
        check(Access.media(media, "di_definition").contains("a%20b.mp4"), "missing quality fallback");
        media.put("isAccess", "-1_03");
        try { Access.media(media, "gao_definition"); throw new AssertionError("restricted URL leaked"); }
        catch (IllegalStateException expected) { checks++; }
        check(Catalog.yearMatches("2018", "2021-2015"), "historic year bucket");
        check(!Catalog.yearMatches("2022", "2021-2015"), "year bucket exclusion");
        JSONArray videos = new JSONArray();
        for (int i=0; i<33; i++) videos.put(new JSONObject().put("id", ""+i).put("title", "量子测试 " + i)
            .put("year", "2020").put("date", i).put("views", 33-i).put("topics", new JSONArray().put("quantum"))
            .put("series", new JSONArray().put("CNCC")));
        Catalog catalog = new Catalog(new JSONObject().put("schema_version", 1).put("complete", true)
            .put("series_complete", true).put("videos", videos).toString());
        HashMap<String,String> filters = new HashMap<>();
        filters.put("dataYear", "2021-2015"); filters.put("seriesText", "CNCC");
        JSONObject page = catalog.query("quantum", "量子", 2, filters);
        check(page.getInt("pagecount") == 3 && page.getJSONArray("list").length() == 16, "pagination");
        check(page.getJSONArray("list").getJSONObject(0).getString("vod_id").equals("16"), "date ordering");
        check(catalog.query("quantum", "", Integer.MAX_VALUE, filters).getJSONArray("list").length() == 0, "overflow safe pagination");
        filters.put("sortRule", "view_count");
        check(catalog.query("quantum", "", 1, filters).getJSONArray("list").getJSONObject(0).getString("vod_id").equals("0"), "numeric popularity sort");
        check(catalog.query("firmware", "", 1, filters).getInt("total") == 0, "topic intersection");
        SessionCookies cookies = new SessionCookies("JSESSIONID=old; theme=dark");
        check(cookies.merge(Arrays.asList("JSESSIONID=new; Path=/; HttpOnly", "route=blue; Secure")), "cookie rotation detected");
        check(cookies.header().equals("JSESSIONID=new; theme=dark; route=blue"), "cookie rotation merged");
        check(cookies.merge(Arrays.asList("theme=; Max-Age=0; Path=/")), "cookie deletion detected");
        check(!cookies.header().contains("theme="), "expired cookie removed");
        String stable = cookies.header();
        try { cookies.merge(Arrays.asList("first=changed", "bad=x\r\nInjected: yes")); throw new AssertionError("cookie injection accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        check(cookies.header().equals(stable), "invalid cookie batch rolls back atomically");
        System.out.println("Java contract checks passed: " + checks);
    }
}
