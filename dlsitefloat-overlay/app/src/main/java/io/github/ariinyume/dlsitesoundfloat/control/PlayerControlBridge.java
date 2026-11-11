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
    private static volatile WeakReference<Object> expoRef = new WeakReference<>(null);
    private static volatile java.util.Map<?, ?> expoStatus;

    private PlayerControlBridge() {}

    public static void observeExpoStatus(Object owner, java.util.Map<?, ?> status) {
        boolean fresh = expoRef.get() != owner;
        expoRef = new WeakReference<>(owner);
        expoStatus = new java.util.HashMap<>(status);
        if (owner.getClass().getName().equals("expo.modules.audio.AudioPlaylist")) observePlaylist(owner);
        if (!fresh && exoRef.get() != null) return;
        // SharedRef's backing field is also renamed by R8; inspect its value type.
        try {
            Class<?> exo = Class.forName("androidx.media3.exoplayer.ExoPlayer", false, owner.getClass().getClassLoader());
            for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    f.setAccessible(true);
                    Object value = f.get(owner);
                    if (exo.isInstance(value)) {
                        observePlayer(value);
                        exoRef = new WeakReference<>(value);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean is2231Player(Object player) {
        // Names verified against DLsiteSound 2.23.1 (633); never guess on other classes.
        return player != null && player.getClass().getName().equals("W2.I");
    }

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
        Object owner = expoRef.get();
        java.util.Map<?, ?> status = expoStatus;
        if (owner != null && status != null) {
            int index = status.get("currentIndex") instanceof Number ? ((Number) status.get("currentIndex")).intValue() : 0;
            try {
                for (Class<?> c = owner.getClass(); c != null && c.getName().startsWith("expo.modules.audio."); c = c.getSuperclass()) {
                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        f.setAccessible(true);
                        Object value = f.get(owner);
                        Object source = value;
                        if (value instanceof java.util.List) {
                            java.util.List<?> list = (java.util.List<?>) value;
                            source = index >= 0 && index < list.size() ? list.get(index) : null;
                        }
                        if (source != null && source.getClass().getName().equals("expo.modules.audio.AudioSource")) {
                            Object uri = XposedCompat.callMethod(source, "getUri");
                            if (uri instanceof String && !((String) uri).isEmpty()) return mediaUri((String) uri);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        Object player = exoRef.get();
        if (player == null) return null;
        try {
            Object item = XposedCompat.callMethod(player, "getCurrentMediaItem");
            if (item == null) return null;
            Object config = field(item, "localConfiguration");
            Object uri = config == null ? null : field(config, "uri");
            if (uri != null) {
                String raw = uri.toString();
                return mediaUri(raw);
            }
            Object id = field(item, "mediaId");
            if (id instanceof String && !((String) id).isEmpty()) return "id:" + id;
        } catch (Throwable ignored) {}
        return null;
    }

    private static String mediaUri(String raw) {
        int query = raw.indexOf('?');
        return "uri:" + (query >= 0 ? raw.substring(0, query) : raw);
    }

    private static Object field(Object value, String name) {
        try {
            java.lang.reflect.Field f = value.getClass().getField(name);
            f.setAccessible(true);
            return f.get(value);
        } catch (Throwable ignored) { return null; }
    }

    public static boolean isPlaying() {
        java.util.Map<?, ?> status = expoStatus;
        if (expoRef.get() != null && status != null && status.get("playing") instanceof Boolean) return (Boolean) status.get("playing");
        Object player = playerRef.get();
        if (player == null) return false;
        try {
            Object value = XposedCompat.callMethod(player, "getPlayWhenReady");
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) { return false; }
    }

    public static long getCurrentPositionMs() {
        java.util.Map<?, ?> status = expoStatus;
        if (expoRef.get() != null && status != null && status.get("currentTime") instanceof Number) {
            return Math.max(0L, (long) (((Number) status.get("currentTime")).doubleValue() * 1000.0));
        }
        Object player = playerRef.get();
        if (player == null) return 0L;
        try {
            Object value = XposedCompat.callMethod(player, "getCurrentPosition");
            return value instanceof Number ? Math.max(0L, ((Number) value).longValue()) : 0L;
        } catch (Throwable ignored) { return 0L; }
    }

    public static long getDurationMs() {
        java.util.Map<?, ?> status = expoStatus;
        if (expoRef.get() != null && status != null && status.get("duration") instanceof Number) {
            return Math.max(0L, (long) (((Number) status.get("duration")).doubleValue() * 1000.0));
        }
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
            if (is2231Player(player)) XposedCompat.callMethod(player, "V", !isPlaying());
            else XposedCompat.callMethod(player, isPlaying() ? "pause" : "play");
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
            XposedCompat.callMethod(player, is2231Player(player) ? "N" : "seekTo", target);
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
            String resolved = method;
            if (is2231Player(exoRef.get())) resolved = "previous".equals(method) ? "p0" : "l0";
            XposedCompat.callMethod(playlist, resolved);
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
