package com.github.catvod.spider.ccf;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Catalog {
    public static final int PAGE_SIZE = 16;
    private final JSONObject root;

    public Catalog(String json) throws Exception {
        root = new JSONObject(json);
        if (root.optInt("schema_version") != 1 || !root.optBoolean("complete") || root.optJSONArray("videos") == null)
            throw new IllegalArgumentException("目录格式无效");
    }

    public JSONObject conditions() throws Exception { return root.getJSONObject("conditions"); }
    public JSONArray topics() throws Exception { return root.getJSONArray("topics"); }
    public String updated() { return root.optString("generated_at"); }

    public static boolean contains(JSONArray array, String value) {
        if (array == null) return false;
        for (int i=0; i<array.length(); i++) if (value.equals(array.optString(i))) return true;
        return false;
    }

    public static boolean yearMatches(String year, String selected) {
        if (selected == null || selected.isEmpty()) return true;
        if (selected.equals(year)) return true;
        if (selected.matches("[0-9]{4}-[0-9]{4}") && year.matches("[0-9]{4}")) {
            int a = Integer.parseInt(selected.substring(0,4)), b = Integer.parseInt(selected.substring(5));
            int y = Integer.parseInt(year);
            return y >= Math.min(a,b) && y <= Math.max(a,b);
        }
        return false;
    }

    public JSONObject query(String topic, String search, int page, Map<String,String> filters) throws Exception {
        List<JSONObject> selected = new ArrayList<>();
        JSONArray videos = root.getJSONArray("videos");
        String year = filters.get("dataYear"), series = filters.get("seriesText");
        if (series != null && !series.isEmpty() && !root.optBoolean("series_complete"))
            throw new IllegalStateException("目录尚未完成系列同步，请使用“CCF视频”原站筛选。");
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        for (int i=0; i<videos.length(); i++) {
            JSONObject v = videos.getJSONObject(i);
            if (topic != null && !topic.isEmpty() && !contains(v.optJSONArray("topics"), topic)) continue;
            if (!yearMatches(v.optString("year"), year)) continue;
            if (series != null && !series.isEmpty() && !contains(v.optJSONArray("series"), series)) continue;
            if (!v.optString("title").toLowerCase(Locale.ROOT).contains(query)) continue;
            selected.add(v);
        }
        final String sort = "view_count".equals(filters.get("sortRule")) ? "views" : "date";
        Collections.sort(selected, (a,b) -> {
            int cmp = Long.compare(b.optLong(sort), a.optLong(sort));
            return cmp != 0 ? cmp : a.optString("id").compareTo(b.optString("id"));
        });
        JSONArray list = new JSONArray();
        long offset = (long)(Math.max(1, page)-1) * PAGE_SIZE;
        for (long i=offset; i<Math.min(offset+PAGE_SIZE, selected.size()); i++) list.put(card(selected.get((int)i)));
        return page(list, page, selected.size());
    }

    public static JSONObject card(JSONObject v) throws Exception {
        return new JSONObject().put("vod_id", v.getString("id")).put("vod_name", v.optString("title"))
            .put("vod_pic", v.optString("cover")).put("vod_remarks", v.optString("access") + " · " + v.optString("year"));
    }

    public static JSONObject page(JSONArray list, int page, int total) throws Exception {
        return new JSONObject().put("list", list).put("page", Math.max(1, page))
            .put("pagecount", Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE)).put("limit", PAGE_SIZE).put("total", total);
    }
}
