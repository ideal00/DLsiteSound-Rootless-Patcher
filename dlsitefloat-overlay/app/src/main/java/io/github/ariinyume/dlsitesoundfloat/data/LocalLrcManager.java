/*
 * DLsiteSound Rootless Patcher - local fan subtitle import
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.ariinyume.dlsitesoundfloat.data;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.ariinyume.dlsitesoundfloat.control.PlayerControlBridge;
import io.github.ariinyume.dlsitesoundfloat.hook.ActivityButtonHook;
import io.github.ariinyume.dlsitesoundfloat.window.BlackScreenLayer;
import io.github.ariinyume.dlsitesoundfloat.util.XposedCompat;
import io.github.libxposed.api.XposedInterface;

/** Imports a Kikoeru.Extras .lrc file for the playing track and caches it in the host app. */
public final class LocalLrcManager {
    private static final int PICK_REQUEST = 0x4c52;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static volatile boolean pickerPending;
    private static volatile String pickerKey;
    private static volatile String activeKey;
    private static volatile String checkedKey;
    private static volatile boolean overrideActive;
    private static volatile long lastCheckMs;

    private LocalLrcManager() {}

    public static void hook(ClassLoader cl) {
        hookResult(Activity.class);
        try {
            hookResult(XposedCompat.findClass("jp.co.eisys.dlsitesound.MainActivity", cl));
        } catch (Throwable ignored) {}
    }

    private static void hookResult(Class<?> activityClass) {
        try {
            XposedCompat.hookAllMethods(activityClass, "onActivityResult", new XposedCompat.VoidHook() {
                @Override protected void afterVoid(XposedInterface.Chain chain) {
                    if (!pickerPending || !(chain.getThisObject() instanceof Activity)) return;
                    Object request = chain.getArg(0);
                    if (!(request instanceof Integer) || (Integer) request != PICK_REQUEST) return;
                    pickerPending = false;
                    Object code = chain.getArg(1);
                    Object data = chain.getArg(2);
                    if (!(code instanceof Integer) || (Integer) code != Activity.RESULT_OK
                            || !(data instanceof Intent)) return;
                    String currentKey = PlayerControlBridge.getCurrentMediaKey();
                    if (pickerKey != null && currentKey != null && !pickerKey.equals(currentKey)) {
                        show((Activity) chain.getThisObject(), "音轨已切换，请重新选择字幕");
                        return;
                    }
                    Uri uri = ((Intent) data).getData();
                    if (uri != null) importUri((Activity) chain.getThisObject(), uri,
                            currentKey != null ? currentKey : pickerKey);
                }
            });
        } catch (Throwable e) {
            XposedCompat.log("[DLsiteSoundFloat:LRC] result hook failed: " + e.getMessage());
        }
    }

    public static void pickCurrentTrack() {
        Activity activity = ActivityButtonHook.currentActivity();
        if (activity == null) return;
        BlackScreenLayer.getInstance().disable();
        pickerKey = PlayerControlBridge.getCurrentMediaKey();
        pickerPending = true;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivityForResult(intent, PICK_REQUEST);
        } catch (Throwable e) {
            pickerPending = false;
            Toast.makeText(activity, "无法打开字幕文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private static void importUri(Activity activity, Uri uri, String keyAtPick) {
        new Thread(() -> {
            try {
                byte[] bytes;
                try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalArgumentException("无法读取文件");
                    bytes = readLimited(input);
                }
                JSONArray cues = parseLrc(new String(bytes, StandardCharsets.UTF_8));
                if (cues.length() == 0) throw new IllegalArgumentException("没有识别到 LRC 时间轴");
                String key = keyAtPick;
                if (key != null) {
                    File file = cachedFile(activity, key);
                    File dir = file.getParentFile();
                    if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
                        throw new IllegalStateException("无法创建字幕缓存");
                    }
                    try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
                }
                activeKey = key;
                checkedKey = key;
                overrideActive = true;
                SubtitleRepository.getInstance().loadFromJsonArray(cues);
                show(activity, "已导入 " + cues.length() + " 行本地字幕");
            } catch (Throwable e) {
                show(activity, "字幕导入失败：" + e.getMessage());
            }
        }, "dlsite-lrc-import").start();
    }

    public static void onTrackChanged() {
        overrideActive = false;
        activeKey = null;
        checkedKey = null;
    }

    public static boolean isOverrideActive() { return overrideActive; }

    /** Called from the player's position callback; disk access runs on a separate thread. */
    public static void maybeRestore(Context context) {
        if (context == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastCheckMs < 500L) return;
        lastCheckMs = now;
        String key = PlayerControlBridge.getCurrentMediaKey();
        if (key == null || key.equals(checkedKey)) return;
        checkedKey = key;
        if (!cachedFile(context, key).isFile()) {
            overrideActive = false;
            activeKey = null;
            return;
        }
        new Thread(() -> {
            try (InputStream input = new FileInputStream(cachedFile(context, key))) {
                JSONArray cues = parseLrc(new String(readLimited(input), StandardCharsets.UTF_8));
                if (cues.length() == 0 || !key.equals(checkedKey)) return;
                activeKey = key;
                overrideActive = true;
                SubtitleRepository.getInstance().loadFromJsonArray(cues);
            } catch (Throwable e) {
                XposedCompat.log("[DLsiteSoundFloat:LRC] restore failed: " + e.getMessage());
            }
        }, "dlsite-lrc-restore").start();
    }

    private static byte[] readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) {
            if (output.size() + n > MAX_BYTES) throw new IllegalArgumentException("字幕文件超过 4 MB");
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }

    private static File cachedFile(Context context, String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte b : hash) name.append(String.format(Locale.US, "%02x", b & 0xff));
            return new File(new File(context.getFilesDir(), "rootless-lrc"), name + ".lrc");
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    private static JSONArray parseLrc(String content) throws Exception {
        List<Entry> entries = new ArrayList<>();
        int offsetMs = 0;
        for (String raw : content.replace("\uFEFF", "").split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("[offset:") && line.endsWith("]")) {
                try { offsetMs = Integer.parseInt(line.substring(8, line.length() - 1)); }
                catch (NumberFormatException ignored) {}
                continue;
            }
            Matcher matcher = STAMP.matcher(line);
            int textStart = 0;
            List<Long> times = new ArrayList<>();
            while (matcher.find()) {
                int minutes = Integer.parseInt(matcher.group(1));
                int seconds = Integer.parseInt(matcher.group(2));
                if (seconds > 59) continue;
                String fraction = matcher.group(3);
                int ms = fraction == null ? 0 : Integer.parseInt(
                        (fraction + "00").substring(0, 3));
                times.add(Math.max(0L, (minutes * 60L + seconds) * 1000L + ms + offsetMs));
                textStart = matcher.end();
            }
            String text = line.substring(textStart).trim();
            if (!text.isEmpty()) for (long ms : times) entries.add(new Entry(ms, text));
        }
        entries.sort(Comparator.comparingLong(e -> e.timeMs));
        JSONArray result = new JSONArray();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            long end = i + 1 < entries.size() ? entries.get(i + 1).timeMs : entry.timeMs + 5000L;
            if (end <= entry.timeMs) end = entry.timeMs + 1000L;
            result.put(new JSONObject()
                    .put("start_time", formatTime(entry.timeMs))
                    .put("end_time", formatTime(end))
                    .put("subtitles", new JSONArray().put(entry.text)));
        }
        return result;
    }

    private static String formatTime(long ms) {
        return String.format(Locale.US, "%02d:%02d:%02d.%03d",
                ms / 3600000L, (ms / 60000L) % 60L, (ms / 1000L) % 60L, ms % 1000L);
    }

    private static void show(Activity activity, String text) {
        activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_LONG).show());
    }

    private static final class Entry {
        final long timeMs;
        final String text;
        Entry(long timeMs, String text) { this.timeMs = timeMs; this.text = text; }
    }
}
