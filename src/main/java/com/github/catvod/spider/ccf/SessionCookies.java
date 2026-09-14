package com.github.catvod.spider.ccf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small, synchronized-by-caller cookie jar for the dl.ccf.org.cn session. */
public final class SessionCookies {
    private static final int MAX_HEADER = 16384;
    private final LinkedHashMap<String,String> values = new LinkedHashMap<>();

    public SessionCookies(String storedHeader) { replace(storedHeader); }

    public void replace(String header) {
        values.clear();
        if (header == null || header.trim().isEmpty()) return;
        validateHeader(header);
        for (String part : header.split(";")) putPair(part);
    }

    /** Merge response Set-Cookie lines and return true when the request header changed. */
    public boolean merge(List<String> setCookies) {
        String before = header();
        try {
            if (setCookies != null) for (String line : setCookies) mergeOne(line);
            String after = header();
            if (after.length() > MAX_HEADER) throw new IllegalArgumentException("Cookie 过大");
            return !before.equals(after);
        } catch (RuntimeException failure) {
            replace(before);
            throw failure;
        }
    }

    public String header() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String,String> item : values.entrySet()) {
            if (out.length() > 0) out.append("; ");
            out.append(item.getKey()).append('=').append(item.getValue());
        }
        return out.toString();
    }

    private void mergeOne(String line) {
        if (line == null || line.trim().isEmpty()) return;
        validateHeader(line);
        String[] fields = line.split(";", -1);
        String pair = fields[0].trim();
        int equals = pair.indexOf('=');
        if (equals <= 0) return;
        String name = pair.substring(0, equals).trim();
        String value = pair.substring(equals + 1).trim();
        if (!validName(name)) return;
        boolean delete = value.isEmpty();
        for (int i=1; i<fields.length; i++) {
            String attribute = fields[i].trim().toLowerCase(Locale.ROOT).replace(" ", "");
            if (attribute.startsWith("max-age=")) {
                try { if (Long.parseLong(attribute.substring(8)) <= 0) delete = true; }
                catch (NumberFormatException ignored) {}
            }
        }
        if (delete) values.remove(name); else values.put(name, value);
    }

    private void putPair(String raw) {
        String pair = raw.trim();
        int equals = pair.indexOf('=');
        if (equals <= 0) return;
        String name = pair.substring(0, equals).trim();
        String value = pair.substring(equals + 1).trim();
        if (validName(name) && !value.isEmpty()) values.put(name, value);
    }

    private static boolean validName(String name) {
        return !name.isEmpty() && name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    }

    private static void validateHeader(String value) {
        if (value.length() > MAX_HEADER || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)
            throw new IllegalArgumentException("Cookie 格式无效");
    }
}
