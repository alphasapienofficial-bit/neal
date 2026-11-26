package com.neal.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Updates the app without a new APK.
 * The page is downloaded from the public GitHub repo, saved in the app's own storage,
 * and Capacitor serves it from there (same address, so all saved data stays).
 * A newly installed APK always wins over an older download.
 */
final class LiveUpdate {
    static final String URL_PAGE = "https://raw.githubusercontent.com/alphasapienofficial-bit/neal/main/www/index.html";
    // Capacitor reads this setting when it starts, and serves the app from that folder.
    private static final String CAP_PREFS = "CapWebViewSettings";
    private static final String CAP_PATH = "serverBasePath";
    private static final String PREFS = "NealLive";
    private static final long MIN_GAP_MS = 5 * 60 * 1000;

    private LiveUpdate() {}

    /** Call before the bridge starts. Drops a downloaded copy when a newer APK was installed after it. */
    static void prepare(Context c) {
        SharedPreferences me = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long installed = 0;
        try { installed = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime; } catch (Exception ignored) {}
        if (me.getLong("apk", -1) != installed) {
            c.getSharedPreferences(CAP_PREFS, Context.MODE_PRIVATE).edit().remove(CAP_PATH).commit();
            me.edit().putLong("apk", installed).remove("hash").remove("pending").commit();
            deleteTree(new File(c.getFilesDir(), "live"));
            return;
        }
        String path = c.getSharedPreferences(CAP_PREFS, Context.MODE_PRIVATE).getString(CAP_PATH, null);
        if (path != null && !new File(path, "index.html").exists()) {
            c.getSharedPreferences(CAP_PREFS, Context.MODE_PRIVATE).edit().remove(CAP_PATH).commit();
            me.edit().remove("hash").commit();
        }
        me.edit().remove("pending").commit();
    }

    /** Pending folder that is ready to show now, or null. */
    static String pending(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("pending", null);
    }

    static void clearPending(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("pending").apply();
    }

    interface Done { void ready(String folder); }

    /** Looks for a newer page in the background. Calls back with the folder when one was saved. */
    static void check(Context c, Done done) {
        SharedPreferences me = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - me.getLong("checked", 0) < MIN_GAP_MS) return;
        me.edit().putLong("checked", now).apply();
        Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                byte[] page = download(URL_PAGE + "?t=" + now);
                String text = new String(page, "UTF-8");
                if (page.length < 20000 || !text.contains("</html>") || !text.contains("nealOpenPinned")) return;
                String hash = sha1(page);
                String current = me.getString("hash", null);
                if (current == null) current = sha1(readAsset(app.getAssets(), "public/index.html"));
                if (hash.equals(current)) return;
                File dir = new File(new File(app.getFilesDir(), "live"), hash);
                deleteTree(dir);
                copyAssets(app.getAssets(), "public", dir);
                try (OutputStream o = new FileOutputStream(new File(dir, "index.html"))) { o.write(page); }
                File live = new File(app.getFilesDir(), "live");
                File[] old = live.listFiles();
                String keep = app.getSharedPreferences(CAP_PREFS, Context.MODE_PRIVATE).getString(CAP_PATH, "");
                if (old != null) for (File f : old) if (!f.equals(dir) && !f.getAbsolutePath().equals(keep)) deleteTree(f);
                app.getSharedPreferences(CAP_PREFS, Context.MODE_PRIVATE).edit().putString(CAP_PATH, dir.getAbsolutePath()).commit();
                me.edit().putString("hash", hash).putString("pending", dir.getAbsolutePath()).commit();
                if (done != null) done.ready(dir.getAbsolutePath());
            } catch (Exception ignored) {
                // No network or GitHub is down: try again next time.
            }
        }).start();
    }

    private static byte[] download(String u) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(20000);
        h.setUseCaches(false);
        h.setRequestProperty("Cache-Control", "no-cache");
        try {
            if (h.getResponseCode() != 200) throw new Exception("HTTP " + h.getResponseCode());
            try (InputStream in = h.getInputStream()) { return readAll(in); }
        } finally { h.disconnect(); }
    }

    private static byte[] readAsset(AssetManager a, String name) throws Exception {
        try (InputStream in = a.open(name)) { return readAll(in); }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toByteArray();
    }

    private static void copyAssets(AssetManager a, String from, File to) throws Exception {
        String[] list = a.list(from);
        if (list == null || list.length == 0) {
            to.getParentFile().mkdirs();
            try (InputStream in = a.open(from); OutputStream o = new FileOutputStream(to)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            }
            return;
        }
        to.mkdirs();
        for (String n : list) copyAssets(a, from + "/" + n, new File(to, n));
    }

    private static String sha1(byte[] b) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(b);
        StringBuilder s = new StringBuilder();
        for (byte x : d) s.append(String.format("%02x", x));
        return s.toString();
    }

    static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
