package com.github.catvod.spider.ccf;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import org.json.JSONObject;

public final class Http {
    public static final String BASE = "https://dl.ccf.org.cn";
    public static final String UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36";

    private Http() {}

    public static final class AuthenticationException extends IllegalStateException {
        AuthenticationException() { super("CCF 登录已失效，需要重新认证。"); }
    }

    public static String read(String url, Map<String,String> form, String cookie) throws Exception {
        return read(url, form, cookie, null);
    }

    public static String read(String url, Map<String,String> form, Account account) throws Exception {
        return read(url, form, account == null ? "" : account.cookie(), account);
    }

    private static String read(String url, Map<String,String> form, String cookie, Account account) throws Exception {
        if (!url.startsWith("https://")) throw new IllegalArgumentException("仅支持 HTTPS");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        // Never forward account credentials through redirects.
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", UA);
        if (new URL(url).getHost().equalsIgnoreCase("dl.ccf.org.cn")) {
            connection.setRequestProperty("Referer", BASE + "/video/videoIndex.html?_ack=1");
            if (cookie != null && !cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
        }
        try {
            if (form != null) {
                StringBuilder encoded = new StringBuilder();
                for (Map.Entry<String,String> item : form.entrySet()) {
                    if (encoded.length() > 0) encoded.append('&');
                    encoded.append(URLEncoder.encode(item.getKey(), "UTF-8")).append('=')
                        .append(URLEncoder.encode(item.getValue(), "UTF-8"));
                }
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                byte[] bytes = encoded.toString().getBytes("UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (account != null) account.accept(url, connection.getHeaderFields());
            if (account != null && (status == HttpURLConnection.HTTP_UNAUTHORIZED || (status >= 300 && status < 400)))
                throw new AuthenticationException();
            if (status != 200) throw new IllegalStateException("请求失败（HTTP " + status + "），请重试或重新登录。");
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] block = new byte[8192]; int n;
                while ((n = in.read(block)) != -1) {
                    out.write(block, 0, n);
                    if (out.size() > 24 * 1024 * 1024) throw new IllegalStateException("响应过大");
                }
                String body = out.toString("UTF-8");
                if (account != null && looksLikeHtmlLogin(body)) throw new AuthenticationException();
                return body;
            }
        } finally { connection.disconnect(); }
    }

    public static JSONObject post(String path, Map<String,String> form, String cookie) throws Exception {
        return postBody(read(BASE + path, form, cookie));
    }

    public static JSONObject post(String path, Map<String,String> form, Account account) throws Exception {
        return postBody(read(BASE + path, form, account));
    }

    private static JSONObject postBody(String body) throws Exception {
        JSONObject response = new JSONObject(body);
        if ((!response.optBoolean("success") || response.optJSONObject("data") == null) && looksLikeLogin(response.toString()))
            throw new AuthenticationException();
        if (!response.optBoolean("success") || response.optJSONObject("data") == null)
            throw new IllegalStateException("CCF 接口异常或登录已失效，请重试。");
        return response.getJSONObject("data");
    }

    private static boolean looksLikeLogin(String value) {
        if (value == null) return false;
        String body = value.toLowerCase(java.util.Locale.ROOT);
        return body.contains("passport.ccf.org.cn") || body.contains("请登录") || body.contains("未登录") ||
            body.contains("not logged") || body.contains("unauthorized") ||
            (body.contains("<html") && (body.contains("/login") || body.contains("登录")));
    }

    private static boolean looksLikeHtmlLogin(String value) {
        if (value == null) return false;
        String body = value.toLowerCase(java.util.Locale.ROOT);
        return body.contains("<html") && (body.contains("passport.ccf.org.cn") || body.contains("/login") || body.contains("登录"));
    }
}
