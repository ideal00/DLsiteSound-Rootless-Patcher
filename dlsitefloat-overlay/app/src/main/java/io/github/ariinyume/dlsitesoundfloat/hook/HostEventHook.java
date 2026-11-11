/*
 * DLsiteSound Rootless Patcher - Expo status adapter
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Uses the status-map strategy documented by DLsiteFloat v2.4.4, preserving rootless v4 controls.
 */
package io.github.ariinyume.dlsitesoundfloat.hook;

import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import io.github.ariinyume.dlsitesoundfloat.control.PlayerControlBridge;
import io.github.ariinyume.dlsitesoundfloat.data.LocalLrcManager;
import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;
import io.github.ariinyume.dlsitesoundfloat.util.XposedCompat;
import io.github.libxposed.api.XposedInterface;

/** Status getter names are obfuscated; their signatures and JS map keys remain stable. */
public final class HostEventHook {
    private static final String TAG = "[DLsiteSoundFloat:HostEvents]";
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final ThreadLocal<Boolean> polling = new ThreadLocal<>();
    private static WeakReference<Object> active = new WeakReference<>(null);
    private static Method statusGetter;
    private static Object lastId;
    private static int lastIndex;
    private static String lastMediaKey;
    private static boolean sawStatus, sawPlaylist, scheduled;
    private HostEventHook() {}

    public static void hook(ClassLoader cl, SubtitleRepository repo) {
        // Leave the original clock as the sole owner on the old, unobfuscated host.
        try {
            Class<?> exo = XposedCompat.findClass("androidx.media3.exoplayer.ExoPlayerImpl", cl);
            XposedCompat.findMethodExact(exo, "getCurrentPosition");
            return;
        } catch (Throwable ignored) {}
        Set<Method> installed = new HashSet<>();
        for (String name : new String[]{"expo.modules.audio.AudioPlaylist", "expo.modules.audio.AudioPlayer"}) {
            try {
                Class<?> cls = XposedCompat.findClass(name, cl);
                for (Class<?> c = cls; c != null && c.getName().startsWith("expo.modules.audio."); c = c.getSuperclass()) {
                    for (Method m : c.getDeclaredMethods()) {
                        if (Modifier.isStatic(m.getModifiers()) || Modifier.isAbstract(m.getModifiers())
                                || m.getParameterTypes().length != 0 || !Map.class.isAssignableFrom(m.getReturnType())
                                || !installed.add(m)) continue;
                        m.setAccessible(true);
                        XposedCompat.hookMethod(m, new XposedCompat.SimpleHook() {
                            @Override protected Object after(XposedInterface.Chain chain, Object result) {
                                try {
                                    if (!Boolean.TRUE.equals(polling.get()) && result instanceof Map) {
                                        consume(chain.getThisObject(), m, (Map<?, ?>) result, repo);
                                    }
                                } catch (Throwable t) { XposedCompat.log(TAG + " status ignored: " + t.getMessage()); }
                                return result;
                            }
                        });
                    }
                }
            } catch (Throwable t) { XposedCompat.log(TAG + " audio adapter unavailable: " + t.getMessage()); }
        }
        XposedCompat.log(TAG + " hooked " + installed.size() + " Expo status getters");
    }

    private static synchronized void consume(Object player, Method getter, Map<?, ?> status, SubtitleRepository repo) {
        Object time = status.get("currentTime");
        if (!(time instanceof Number) || !(status.get("playing") instanceof Boolean)) return;
        double seconds = ((Number) time).doubleValue();
        if (!Double.isFinite(seconds) || seconds < 0) return;
        // Expo retains a zero-valued AudioPlayer alongside the actual playlist. It must
        // never overwrite the playlist's clock (a regression already documented upstream).
        boolean playlist = status.get("trackCount") instanceof Number;
        if (playlist && ((Number) status.get("trackCount")).intValue() <= 0) return;
        if (sawPlaylist && !playlist) return;
        if (playlist) sawPlaylist = true;
        Object old = active.get();
        if (old != null && old != player && !Boolean.TRUE.equals(status.get("playing"))
                && !Boolean.TRUE.equals(status.get("isLoaded"))) return;
        Object id = status.get("id");
        int index = status.get("currentIndex") instanceof Number ? ((Number) status.get("currentIndex")).intValue() : 0;
        PlayerControlBridge.observeExpoStatus(player, status);
        String key = PlayerControlBridge.getCurrentMediaKey();
        boolean changed = sawStatus && (old != player || !Objects.equals(lastId, id) || lastIndex != index
                || (lastMediaKey != null && key != null && !lastMediaKey.equals(key)));
        active = new WeakReference<>(player);
        statusGetter = getter;
        lastId = id;
        lastIndex = index;
        lastMediaKey = key;
        if (changed) {
            LocalLrcManager.onTrackChanged();
            repo.onTrackChanged("Expo status media identity changed");
        }
        if (!sawStatus) XposedCompat.log(TAG + " receiving Expo playback status");
        sawStatus = true;
        repo.setPlaybackPositionMs((long) (seconds * 1000.0));
        repo.setPlaying((Boolean) status.get("playing"));
        Object state = status.get("playbackState");
        if ("ended".equals(state) || Boolean.TRUE.equals(status.get("didJustFinish"))) repo.setPlaybackState(4);
        else if ("ready".equals(state) || Boolean.TRUE.equals(status.get("isLoaded"))) repo.setPlaybackState(3);
        LocalLrcManager.maybeRestore(ActivityButtonHook.currentActivity());
        if (!scheduled) {
            scheduled = true;
            handler.postDelayed(() -> poll(repo), 250L);
        }
    }

    private static void poll(SubtitleRepository repo) {
        Object player;
        Method getter;
        synchronized (HostEventHook.class) {
            scheduled = false;
            player = active.get();
            getter = statusGetter;
        }
        if (player == null || getter == null) return;
        try {
            polling.set(true);
            Object result = getter.invoke(player);
            if (result instanceof Map) consume(player, getter, (Map<?, ?>) result, repo);
        } catch (Throwable ignored) {
            // A disposed player must not affect the host. A new live getter re-arms polling.
        } finally { polling.remove(); }
    }
}
