package com.github.catvod.crawler;

import android.content.Context;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compile-time SPI only. Never included in the distributed dex JAR. */
public abstract class Spider {
    public void init(Context context, String extend) throws Exception {}
    public String homeContent(boolean filter) throws Exception { return ""; }
    public String homeVideoContent() throws Exception { return ""; }
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String,String> extend) throws Exception { return ""; }
    public String detailContent(List<String> ids) throws Exception { return ""; }
    public String searchContent(String key, boolean quick) throws Exception { return ""; }
    public String searchContent(String key, boolean quick, String pg) throws Exception { return ""; }
    public String playerContent(String flag, String id, List<String> flags) throws Exception { return ""; }
    public String action(String action) throws Exception { return ""; }
    public boolean isVideoFormat(String url) throws Exception { return false; }
    public boolean manualVideoCheck() throws Exception { return false; }
    public Object[] proxy(Map<String,String> params) throws Exception { return null; }
    public void destroy() {}
}
