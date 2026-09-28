/*
 * DLsiteSound Rootless Patcher - touch-blocking black screen
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.ariinyume.dlsitesoundfloat.window;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import io.github.ariinyume.dlsitesoundfloat.util.XposedCompat;

/** A full-screen window placed below the floating subtitles and their controls. */
public final class BlackScreenLayer {
    private static final String TAG = "[DLsiteSoundFloat:BlackScreen]";
    private static final BlackScreenLayer INSTANCE = new BlackScreenLayer();

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private View blocker;
    private boolean enabled;

    private BlackScreenLayer() {}

    public static BlackScreenLayer getInstance() {
        return INSTANCE;
    }

    /** Attach before the subtitle window so the subtitle and controls remain above the blocker. */
    public void attach(Context context, WindowManager manager) {
        detach();
        if (context == null || manager == null) return;
        try {
            View view = new View(context);
            view.setBackgroundColor(Color.BLACK);
            view.setOnTouchListener((v, event) -> true);
            WindowManager.LayoutParams layout = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            layout.gravity = Gravity.TOP | Gravity.START;
            layout.alpha = 0f;
            manager.addView(view, layout);
            windowManager = manager;
            blocker = view;
            params = layout;
        } catch (Throwable error) {
            XposedCompat.log(TAG + " attach failed: " + error.getMessage());
            detach();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean toggle() {
        if (blocker == null || params == null || windowManager == null) return false;
        enabled = !enabled;
        params.alpha = enabled ? 1f : 0f;
        if (enabled) {
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        try {
            windowManager.updateViewLayout(blocker, params);
        } catch (Throwable error) {
            enabled = false;
            XposedCompat.log(TAG + " update failed: " + error.getMessage());
            detach();
        }
        return enabled;
    }

    public void disable() {
        if (enabled) toggle();
    }

    public void detach() {
        enabled = false;
        if (windowManager != null && blocker != null) {
            try {
                windowManager.removeView(blocker);
            } catch (Throwable ignored) {}
        }
        blocker = null;
        params = null;
        windowManager = null;
    }
}
