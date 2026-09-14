package com.github.catvod.spider.ccf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Account implements Application.ActivityLifecycleCallbacks {
    private final Context app;
    private final SharedPreferences prefs;
    private final SessionCookies cookies;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WeakReference<Activity> foreground = new WeakReference<>(null);
    private long lastRecoveryAt;

    public Account(Context context) {
        app = context.getApplicationContext();
        prefs = app.getSharedPreferences("ccf_video_account_v1", Context.MODE_PRIVATE);
        SessionCookies restored;
        try { restored = new SessionCookies(prefs.getString("cookie", "")); }
        catch (IllegalArgumentException ignored) {
            restored = new SessionCookies("");
            prefs.edit().remove("cookie").apply();
        }
        cookies = restored;
        if (context instanceof Activity) foreground = new WeakReference<>((Activity) context);
        if (app instanceof Application) ((Application) app).registerActivityLifecycleCallbacks(this);
    }

    public synchronized String cookie() { return cookies.header(); }
    public boolean configured() { return !cookie().isEmpty(); }

    private synchronized void save(String cookie) {
        cookies.replace(cookie == null ? "" : cookie.trim());
        persist();
    }

    private synchronized void persist() {
        prefs.edit().putString("cookie", cookies.header()).apply();
    }

    /** Accept session rotation from HttpURLConnection and mirror it into WebView SSO state. */
    public synchronized void accept(String url, Map<String,List<String>> headers) {
        if (!dlHost(url) || headers == null) return;
        List<String> received = new ArrayList<>();
        for (Map.Entry<String,List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && "set-cookie".equalsIgnoreCase(entry.getKey()) && entry.getValue() != null)
                received.addAll(entry.getValue());
        }
        if (received.isEmpty()) return;
        try { if (cookies.merge(received)) persist(); }
        catch (IllegalArgumentException ignored) { return; }
        main.post(() -> {
            CookieManager cm = CookieManager.getInstance();
            for (String line : received) cm.setCookie(url, line);
            cm.flush();
        });
    }

    private synchronized void importWebCookies(String url) {
        String value = CookieManager.getInstance().getCookie(url);
        if (value == null || value.trim().isEmpty()) return;
        try {
            SessionCookies incoming = new SessionCookies(value);
            List<String> lines = new ArrayList<>();
            for (String pair : incoming.header().split(";")) lines.add(pair.trim());
            if (cookies.merge(lines)) persist();
        } catch (IllegalArgumentException ignored) {}
    }

    public void message(String text) { main.post(() -> Toast.makeText(app, text, Toast.LENGTH_LONG).show()); }

    public void show() {
        // Detail screens commonly become resumed just after the source callback completes.
        main.postDelayed(() -> {
            Activity activity = current();
            if (activity == null) {
                message("无法打开账号窗口，请退出详情页重进，或在影视仓切换页面后重试。");
                return;
            }
            new AlertDialog.Builder(activity).setTitle("CCF账号 · 仅保存在本设备")
                .setItems(new String[]{"打开 CCF 官方登录页", "导入本设备 Cookie", "清除本源登录凭据"}, (dialog, index) -> {
                    if (index == 0) login(activity);
                    else if (index == 1) paste(activity);
                    else {
                        save("");
                        // Do not removeAllCookies(): the host may have other sources' accounts.
                        clearCcfCookies();
                        message("已清除本源凭据；下次打开官网可检查是否仍有单点登录会话。");
                    }
                }).setNegativeButton("返回", null).show();
        }, 500);
    }

    private void paste(Activity activity) {
        EditText input = new EditText(activity);
        input.setHint("从已登录的 dl.ccf.org.cn 复制 Cookie 请求头");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(activity).setTitle("本地 Cookie 导入").setView(input)
            .setPositiveButton("保存", (d, w) -> {
                try {
                    String value = input.getText().toString().trim();
                    if (!value.contains("=") || value.startsWith("{")) throw new IllegalArgumentException();
                    save(value);
                    message("已保存，是否有效将在播放时由 CCF 验证。");
                } catch (Exception e) { message("请输入 Cookie 请求头的值，不含 Cookie: 前缀或换行。"); }
            }).setNegativeButton("取消", null).show();
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void login(Activity activity) {
        WebView web = new WebView(activity);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return !Access.ccfHost(url); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) { return !Access.ccfHost(req.getUrl().toString()); }
        });
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("CCF官方登录 · 登录后点“完成”")
            .setView(web).setPositiveButton("完成", null).setNegativeButton("取消", null).create();
        dialog.setOnDismissListener(d -> { web.stopLoading(); web.destroy(); });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(
            (int)(activity.getResources().getDisplayMetrics().widthPixels * .94),
            (int)(activity.getResources().getDisplayMetrics().heightPixels * .92));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = CookieManager.getInstance().getCookie(Http.BASE + "/video/videoDetail.html");
            if (value == null || value.isEmpty()) { message("尚未取得数字图书馆会话，请完成登录并返回 CCF 页面。"); return; }
            save(value);
            CookieManager.getInstance().flush();
            message("已保存本设备会话；播放时会检查账号权限。");
            dialog.dismiss();
        });
        web.loadUrl(Http.BASE + "/login?service=https%3A%2F%2Fdl.ccf.org.cn%2Fvideo%2FvideoIndex.html");
    }

    /** Best-effort silent SSO renewal. A failed or interactive login is never bypassed. */
    public boolean recoverSession() {
        if (Looper.myLooper() == Looper.getMainLooper()) return false;
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (now - lastRecoveryAt < 60_000L) return false;
            lastRecoveryAt = now;
        }
        Activity activity = current();
        if (activity == null) return false;
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean returned = new AtomicBoolean(false);
        main.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) { finished.countDown(); return; }
            WebView web = new WebView(activity);
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setSupportMultipleWindows(false);
            CookieManager.getInstance().setAcceptCookie(true);
            AtomicBoolean closed = new AtomicBoolean(false);
            Runnable close = () -> {
                if (!closed.compareAndSet(false, true)) return;
                web.stopLoading();
                web.destroy();
                finished.countDown();
            };
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return !Access.ccfHost(url); }
                @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) { return !Access.ccfHost(req.getUrl().toString()); }
                @Override public void onPageFinished(WebView v, String url) {
                    if (!dlHost(url) || url.contains("/login")) return;
                    importWebCookies(url);
                    returned.set(true);
                    close.run();
                }
                @Override public void onReceivedError(WebView v, int code, String description, String failingUrl) {
                    if (failingUrl != null && failingUrl.equals(v.getUrl())) close.run();
                }
            });
            main.postDelayed(close, 12_000L);
            web.loadUrl(Http.BASE + "/login?service=https%3A%2F%2Fdl.ccf.org.cn%2Fvideo%2FvideoIndex.html");
        });
        try { finished.await(13, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        return returned.get();
    }

    private static boolean dlHost(String url) {
        try { return "dl.ccf.org.cn".equalsIgnoreCase(new URI(url).getHost()); }
        catch (Exception e) { return false; }
    }

    private void clearCcfCookies() {
        CookieManager cm = CookieManager.getInstance();
        for (String host : new String[]{"dl.ccf.org.cn", "passport.ccf.org.cn", "web.ccf.org.cn"}) {
            String value = cm.getCookie("https://" + host + "/");
            if (value == null) continue;
            for (String part : value.split(";")) {
                String name = part.split("=", 2)[0].trim();
                if (name.isEmpty()) continue;
                cm.setCookie("https://" + host, name + "=; Max-Age=0; Path=/; Secure");
                cm.setCookie("https://" + host, name + "=; Max-Age=0; Domain=.ccf.org.cn; Path=/; Secure");
            }
        }
        cm.flush();
    }

    private Activity current() {
        Activity a = foreground.get();
        if (a != null && !a.isFinishing() && !a.isDestroyed()) return a;
        // Older TVBox variants pass only Application, without exposing an Activity API.
        // Best-effort fallback; Android may block this. Lifecycle tracking is the primary path.
        try {
            Class<?> threadClass = Class.forName("android.app.ActivityThread");
            Object thread = threadClass.getMethod("currentActivityThread").invoke(null);
            Field activities = threadClass.getDeclaredField("mActivities"); activities.setAccessible(true);
            for (Object record : ((Map<?,?>) activities.get(thread)).values()) {
                Field paused = record.getClass().getDeclaredField("paused"); paused.setAccessible(true);
                if (paused.getBoolean(record)) continue;
                Field activity = record.getClass().getDeclaredField("activity"); activity.setAccessible(true);
                a = (Activity) activity.get(record);
                if (!a.isFinishing() && !a.isDestroyed()) return a;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void close() { if (app instanceof Application) ((Application) app).unregisterActivityLifecycleCallbacks(this); }
    public void onActivityResumed(Activity a) { foreground = new WeakReference<>(a); }
    public void onActivityCreated(Activity a, Bundle b) {}
    public void onActivityStarted(Activity a) {}
    public void onActivityPaused(Activity a) {}
    public void onActivityStopped(Activity a) {}
    public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    public void onActivityDestroyed(Activity a) { if (foreground.get() == a) foreground.clear(); }
}
