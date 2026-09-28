/*
 * DLsiteSound Rootless Patcher - floating player enhancements
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.ariinyume.dlsitesoundfloat.control;

import java.lang.ref.WeakReference;
import io.github.ariinyume.dlsitesoundfloat.util.XposedCompat;

public final class PlayerControlBridge {
    private static final String TAG = "[DLsiteSoundFloat:Control]";
    private static volatile WeakReference<Object> playerRef = new WeakReference<>(null);
    private static volatile WeakReference<Object> exoRef = new WeakReference<>(null);
    private static volatile WeakReference<Object> playlistRef = new WeakReference<>(null);

    private PlayerControlBridge() {}

    public static void observePlayer(Object player) {
        if (player != null && playerRef.get() != player) playerRef = new WeakReference<>(player);
        if (player != null && player.getClass().getName().contains("ExoPlayerImpl")) {
            exoRef = new WeakReference<>(player);
        }
    }

    public static void observePlaylist(Object playlist) {
        if (playlist != null && playlistRef.get() != playlist) playlistRef = new WeakReference<>(playlist);
    }

    public static boolean hasPlayer() { return playerRef.get() != null; }

    /** Stable identifier for the current audio item, used only for local subtitle cache lookup. */
    public static String getCurrentMediaKey() {
        Object player = exoRef.get();
        if (player == null) return null;
        try {
            Object item = XposedCompat.callMethod(player, "getCurrentMediaItem");
            if (item == null) return null;
            Object config = field(item, "localConfiguration");
            Object uri = config == null ? null : field(config, "uri");
            if (uri != null) {
                String raw = uri.toString();
                int query = raw.indexOf('?');
                return "uri:" + (query >= 0 ? raw.substring(0, query) : raw);
            }
            Object id = field(item, "mediaId");
            if (id instanceof String && !((String) id).isEmpty()) return "id:" + id;
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object field(Object value, String name) {
        try {
            java.lang.reflect.Field f = value.getClass().getField(name);
            f.setAccessible(true);
            return f.get(value);
        } catch (Throwable ignored) { return null; }
    }

    public static boolean isPlaying() {
        Object player = playerRef.get();
        if (player == null) return false;
        try {
            Object value = XposedCompat.callMethod(player, "getPlayWhenReady");
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) { return false; }
    }

    public static long getCurrentPositionMs() {
        Object player = playerRef.get();
        if (player == null) return 0L;
        try {
            Object value = XposedCompat.callMethod(player, "getCurrentPosition");
            return value instanceof Number ? Math.max(0L, ((Number) value).longValue()) : 0L;
        } catch (Throwable ignored) { return 0L; }
    }

    public static long getDurationMs() {
        Object player = playerRef.get();
        if (player == null) return 0L;
        try {
            Object value = XposedCompat.callMethod(player, "getDuration");
            long duration = value instanceof Number ? ((Number) value).longValue() : 0L;
            return duration > 0 ? duration : 0L;
        } catch (Throwable ignored) { return 0L; }
    }

    public static void togglePlayPause() {
        Object player = playerRef.get();
        if (player == null) return;
        try {
            XposedCompat.callMethod(player, isPlaying() ? "pause" : "play");
        } catch (Throwable t) {
            XposedCompat.log(TAG + " toggle failed: " + t.getMessage());
        }
    }

    public static void seekBy(long deltaMs) { seekTo(getCurrentPositionMs() + deltaMs); }

    public static void seekTo(long positionMs) {
        Object player = playerRef.get();
        if (player == null) return;
        long duration = getDurationMs();
        long target = Math.max(0L, positionMs);
        if (duration > 0) target = Math.min(target, duration);
        try {
            XposedCompat.callMethod(player, "seekTo", target);
        } catch (Throwable t) {
            XposedCompat.log(TAG + " seekTo(" + target + ") failed: " + t.getMessage());
        }
    }

    public static void previous() {
        if (!callPlaylist("previous")) callPlayerFallback("seekToPreviousMediaItem");
    }

    public static void next() {
        if (!callPlaylist("next")) callPlayerFallback("seekToNextMediaItem");
    }

    private static boolean callPlaylist(String method) {
        Object playlist = playlistRef.get();
        if (playlist == null) return false;
        try {
            XposedCompat.callMethod(playlist, method);
            return true;
        } catch (Throwable t) {
            XposedCompat.log(TAG + " playlist." + method + " failed: " + t.getMessage());
            return false;
        }
    }

    private static void callPlayerFallback(String method) {
        Object player = playerRef.get();
        if (player == null) return;
        try {
            XposedCompat.callMethod(player, method);
        } catch (Throwable t) {
            XposedCompat.log(TAG + " fallback " + method + " failed: " + t.getMessage());
        }
    }
}
