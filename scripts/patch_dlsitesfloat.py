#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_dlsitesfloat.py /path/to/DLSiteSoundFloatingSubtitle")
root = Path(sys.argv[1]).resolve()

def patch(path, old, new, label):
    p = root / path
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"DLsiteFloat upstream changed; missing anchor: {label} ({path})")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")

p = "app/src/main/java/io/github/ariinyume/dlsitesoundfloat/hook/PlayerPositionHook.java"
patch(p, "import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;\n",
      "import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;\nimport io.github.ariinyume.dlsitesoundfloat.control.PlayerControlBridge;\n",
      "PlayerPositionHook import")
patch(p, "                    if (r instanceof Long) {\n                        feed((Long) r, PRIO_EXO);\n",
      "                    if (r instanceof Long) {\n                        PlayerControlBridge.observePlayer(chain.getThisObject());\n                        feed((Long) r, PRIO_EXO);\n",
      "PlayerPositionHook observe player")

p = "app/src/main/java/io/github/ariinyume/dlsitesoundfloat/hook/PlayerSourceHook.java"
patch(p, "import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;\n",
      "import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;\nimport io.github.ariinyume.dlsitesoundfloat.control.PlayerControlBridge;\n",
      "PlayerSourceHook import")
patch(p, '                protected Object after(XposedInterface.Chain chain, Object r) {\n                    String where = className + "." + name;\n',
      '                protected Object after(XposedInterface.Chain chain, Object r) {\n                    if ("expo.modules.audio.AudioPlaylist".equals(className)) {\n                        PlayerControlBridge.observePlaylist(chain.getThisObject());\n                    }\n                    String where = className + "." + name;\n',
      "PlayerSourceHook observe playlist")

p = "app/src/main/java/io/github/ariinyume/dlsitesoundfloat/view/FloatingSubtitleView.java"
patch(p, "import android.graphics.Paint;\n",
      "import android.graphics.Color;\nimport android.graphics.Paint;\n",
      "FloatingSubtitleView Color import")
patch(p, "    private CloseButtonView closeBtn;\n",
      "    private CloseButtonView closeBtn;\n    private PlaybackControlsView playbackControls;\n    private GripIndicatorView grip;\n    private boolean editMode = false;\n",
      "FloatingSubtitleView controls field")
patch(p, "    public void onPanelTapped() {\n        if (closeBtn == null) {\n",
      "    public void onPanelTapped() {\n        if (!editMode) return;\n        if (playbackControls != null) { playbackControls.toggleControls(); }\n        if (closeBtn == null) {\n",
      "FloatingSubtitleView panel tap")
patch(p, "        panel.setAlpha(blurActive ? 242 : 255);\n",
      "        int opacityPct = getContext().getSharedPreferences(PlaybackControlsView.PREFS, Context.MODE_PRIVATE)\n                .getInt(PlaybackControlsView.PREF_PANEL_OPACITY, PlaybackControlsView.DEFAULT_PANEL_OPACITY);\n        opacityPct = Math.max(20, Math.min(100, opacityPct));\n        panel.setAlpha(Math.round(255f * opacityPct / 100f));\n",
      "FloatingSubtitleView panel opacity")
patch(p, "    private void applyPanelBackground() {\n        // v13",
      "    private void applyPanelBackground() {\n        if (!editMode) { setBackground(null); return; }\n        // v13",
      "FloatingSubtitleView transparent display mode")
patch(p, "        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);\n",
      "        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(12f, getSubtitleFontSizeSp() - 3f));\n",
      "FloatingSubtitleView hint font size")
patch(p, "            styleLine(tv, 18f, 0xFFFFFFFF, 1.0f, false);\n",
      "            styleLine(tv, getSubtitleFontSizeSp(), getSubtitleTextColor(), 1.0f, false);\n",
      "FloatingSubtitleView mirrored font size")
patch(p, "                styleLine(tv, BASE_TEXT_SP * CURRENT_SCALE, 0xFFFFFFFF, 1.0f, true);\n",
      "                styleLine(tv, getSubtitleFontSizeSp() * CURRENT_SCALE, getSubtitleTextColor(), 1.0f, true);\n",
      "FloatingSubtitleView current font size")
patch(p, "                    styleLine(tv, 15f, 0x99FFFFFF, 0.35f, false);\n",
      "                    styleLine(tv, Math.max(11f, getSubtitleFontSizeSp() - 2f), getSubtitleTextColor(), 0.35f, false);\n",
      "FloatingSubtitleView context font size")
patch(p, "                BASE_TEXT_SP * CURRENT_SCALE, getContext().getResources().getDisplayMetrics());\n",
      "                getSubtitleFontSizeSp() * CURRENT_SCALE, getContext().getResources().getDisplayMetrics());\n",
      "FloatingSubtitleView measurement font size")
patch(p, "    /** 重算并把当前字幕行滚动到悬浮窗垂直居中（缩放窗口后调用；v20：不带动画，避免拖拽时抖动）。 */\n",
      "    private float getSubtitleFontSizeSp() {\n        int value = getContext().getSharedPreferences(PlaybackControlsView.PREFS, Context.MODE_PRIVATE)\n                .getInt(PlaybackControlsView.PREF_FONT_SIZE_SP, PlaybackControlsView.DEFAULT_FONT_SIZE_SP);\n        return Math.max(13, Math.min(24, value));\n    }\n\n    private int getSubtitleTextColor() {\n        return getContext().getSharedPreferences(PlaybackControlsView.PREFS, Context.MODE_PRIVATE)\n                .getInt(PlaybackControlsView.PREF_TEXT_COLOR, PlaybackControlsView.DEFAULT_TEXT_COLOR);\n    }\n\n    private int getContrastShadowColor() {\n        int c = getSubtitleTextColor();\n        double luminance = (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255.0;\n        return luminance > 0.48 ? 0xE6000000 : 0xE6FFFFFF;\n    }\n\n    public void setEditMode(boolean edit) {\n        editMode = edit;\n        applyPanelBackground();\n        if (grip != null) grip.setVisibility(edit ? VISIBLE : GONE);\n        if (closeBtn != null) {\n            closeBtnHandler.removeCallbacks(closeBtnHideTask);\n            closeBtn.setVisibility(GONE);\n        }\n        if (playbackControls != null) {\n            if (edit) playbackControls.showControls(); else playbackControls.hideControls();\n        }\n        lastRenderKey = null;\n        updateFromRepository();\n        requestLayout();\n        invalidate();\n    }\n\n    public boolean isEditMode() { return editMode; }\n\n    private void applyAppearanceSettings() {\n        applyPanelBackground();\n        if (hint != null) {\n            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(12f, getSubtitleFontSizeSp() - 3f));\n            hint.setTextColor(getSubtitleTextColor());\n            hint.setShadowLayer(TEXT_SHADOW_RADIUS, 0f, dp(1), getContrastShadowColor());\n        }\n        lastRenderKey = null;\n        updateFromRepository();\n        requestLayout();\n    }\n\n    /** 重算并把当前字幕行滚动到悬浮窗垂直居中（缩放窗口后调用；v20：不带动画，避免拖拽时抖动）。 */\n",
      "FloatingSubtitleView appearance helpers")
patch(p, "        GripIndicatorView grip = new GripIndicatorView(getContext());\n",
      "        grip = new GripIndicatorView(getContext());\n",
      "FloatingSubtitleView grip field")
patch(p, "        closeBtn.setOnClickListener(v -> {\n",
      "        playbackControls = new PlaybackControlsView(getContext(), this::applyAppearanceSettings);\n        FrameLayout.LayoutParams controlsLp = new FrameLayout.LayoutParams(\n                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);\n        controlsLp.gravity = Gravity.BOTTOM;\n        controlsLp.leftMargin = dp(10);\n        controlsLp.rightMargin = dp(46);\n        controlsLp.bottomMargin = dp(10);\n        playbackControls.setLayoutParams(controlsLp);\n\n        closeBtn.setOnClickListener(v -> {\n",
      "FloatingSubtitleView create controls")
patch(p, "        playbackControls.setLayoutParams(controlsLp);\n",
      "        playbackControls.setLayoutParams(controlsLp);\n        playbackControls.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateSubtitleViewport());\n",
      "FloatingSubtitleView reserve subtitle viewport when controls change")
patch(p, "    public boolean isEditMode() { return editMode; }\n",
      "    private void updateSubtitleViewport() {\n        if (scrollView == null || playbackControls == null) return;\n        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) scrollView.getLayoutParams();\n        int reserved = playbackControls.getVisibility() == VISIBLE\n                ? playbackControls.getHeight() + dp(10) : 0;\n        if (lp.bottomMargin == reserved) return;\n        lp.bottomMargin = reserved;\n        scrollView.setLayoutParams(lp);\n        post(this::recenterCurrent);\n    }\n\n    public boolean isEditMode() { return editMode; }\n",
      "FloatingSubtitleView viewport helper")
patch(p, "            if (edit) playbackControls.showControls(); else playbackControls.hideControls();\n        }\n        lastRenderKey = null;\n",
      "            if (edit) playbackControls.showControls(); else playbackControls.hideControls();\n        }\n        updateSubtitleViewport();\n        lastRenderKey = null;\n",
      "FloatingSubtitleView refresh viewport on mode change")
patch(p, "        addView(grip);\n        addView(closeBtn); // 最后添加，保证在最上层、可点击\n",
      "        addView(grip);\n        addView(playbackControls);\n        addView(closeBtn); // 最后添加，保证在最上层、可点击\n        setEditMode(false);\n",
      "FloatingSubtitleView attach controls")
patch(p, "    public boolean hitResizeArea(float x, float y) {\n",
      "    public boolean hitResizeArea(float x, float y) {\n        if (!editMode) return false;\n",
      "FloatingSubtitleView resize only in edit mode")
patch(p, "    @Override\n    protected void onDetachedFromWindow() {\n",
      "    public boolean hitInteractiveControlArea(float x, float y) {\n        if (!editMode) return false;\n        if (playbackControls != null && playbackControls.containsPoint(x, y)) return true;\n        return closeBtn != null && closeBtn.getVisibility() == VISIBLE\n                && x >= closeBtn.getLeft() && x <= closeBtn.getRight()\n                && y >= closeBtn.getTop() && y <= closeBtn.getBottom();\n    }\n\n    public boolean isWindowLocked() {\n        return playbackControls != null && playbackControls.isWindowLocked();\n    }\n\n    @Override\n    protected void onDetachedFromWindow() {\n",
      "FloatingSubtitleView interaction helpers")
patch(p, "            int from = Math.max(0, currentIdx - WINDOW_RADIUS);\n            int to = Math.min(cues.size() - 1, (currentIdx < 0 ? 0 : currentIdx) + WINDOW_RADIUS);\n",
      "            int focus = currentIdx < 0 ? 0 : currentIdx;\n            int from = editMode ? Math.max(0, currentIdx - WINDOW_RADIUS) : focus;\n            int to = editMode ? Math.min(cues.size() - 1, focus + WINDOW_RADIUS) : focus;\n",
      "FloatingSubtitleView current-only display mode")

patch(p, "        tv.setTextColor(color);\n        tv.setAlpha(alpha);\n        tv.setShadowLayer(TEXT_SHADOW_RADIUS, 0f, dp(1), TEXT_SHADOW_COLOR);\n",
      "        tv.setTextColor(color);\n        tv.setAlpha(alpha);\n        tv.setShadowLayer(TEXT_SHADOW_RADIUS, 0f, 0f, getContrastShadowColor());\n",
      "FloatingSubtitleView contrast shadow")

p = "app/src/main/java/io/github/ariinyume/dlsitesoundfloat/window/FloatingWindowManager.java"
patch(p, "import android.graphics.PixelFormat;\n",
      "import android.graphics.Color;\nimport android.graphics.PixelFormat;\nimport android.graphics.drawable.GradientDrawable;\n",
      "FloatingWindowManager handle imports")
patch(p, "import android.view.WindowManager;\n",
      "import android.view.WindowManager;\nimport android.widget.TextView;\n",
      "FloatingWindowManager TextView import")
patch(p, "import io.github.ariinyume.dlsitesoundfloat.view.FloatingSubtitleView;\n",
      "import io.github.ariinyume.dlsitesoundfloat.view.FloatingSubtitleView;\nimport io.github.ariinyume.dlsitesoundfloat.window.BlackScreenLayer;\n",
      "FloatingWindowManager black screen import")
patch(p, "    private static int sLastY = Integer.MIN_VALUE;\n",
      '    private static int sLastY = Integer.MIN_VALUE;\n    private static boolean sGeometryLoaded = false;\n    private static final String UI_PREFS = "dlsitefloat_overlay_ui";\n',
      "FloatingWindowManager geometry fields")
patch(p, "    private WindowManager.LayoutParams params;\n",
      "    private WindowManager.LayoutParams params;\n    private TextView modeHandle;\n    private WindowManager.LayoutParams modeHandleParams;\n    private boolean editMode = false;\n",
      "FloatingWindowManager mode fields")
patch(p, "                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE\n                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,\n",
      "                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE\n                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE\n                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,\n",
      "FloatingWindowManager display flags")
patch(p, "            params.y = y;\n",
      "            params.y = y;\n            params.alpha = 0.78f; // Android 12+ untrusted-touch safe default\n",
      "FloatingWindowManager display alpha")
patch(p, "            wm.addView(view, params);\n",
      "            BlackScreenLayer.getInstance().attach(ctx, wm);\n            wm.addView(view, params);\n            view.setEditMode(false);\n            showModeHandle(ctx);\n",
      "FloatingWindowManager show mode handle")
patch(p, "            int screenH = ctx.getResources().getDisplayMetrics().heightPixels;\n",
      "            int screenH = ctx.getResources().getDisplayMetrics().heightPixels;\n            loadPersistentGeometry(ctx);\n",
      "FloatingWindowManager load geometry")
patch(p, "    private void hide() {\n",
      "    private void setEditMode(boolean edit) {\n        if (wm == null || view == null || params == null) return;\n        editMode = edit;\n        int keep = params.flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND;\n        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE\n                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | keep;\n        if (!edit) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;\n        params.flags = flags;\n        params.alpha = edit ? 1.0f : 0.78f;\n        view.setEditMode(edit);\n        try { wm.updateViewLayout(view, params); } catch (Throwable ignored) {}\n        if (modeHandle != null) {\n            modeHandle.setText(edit ? \"✓\" : \"✎\");\n            modeHandle.setContentDescription(edit ? \"完成字幕调整\" : \"调整字幕\");\n            modeHandle.setBackground(makeHandleBackground(edit));\n        }\n        updateModeHandlePosition();\n        XposedCompat.log(TAG + \" mode=\" + (edit ? \"EDIT\" : \"DISPLAY_CLICK_THROUGH\"));\n    }\n\n    private void showModeHandle(Context ctx) {\n        if (wm == null || params == null || modeHandle != null) return;\n        try {\n            modeHandle = new TextView(ctx);\n            modeHandle.setText(\"✎\");\n            modeHandle.setTextColor(Color.WHITE);\n            modeHandle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);\n            modeHandle.setGravity(Gravity.CENTER);\n            modeHandle.setContentDescription(\"调整字幕\");\n            modeHandle.setClickable(true);\n            modeHandle.setFocusable(false);\n            modeHandle.setElevation(dp(ctx, 8));\n            modeHandle.setBackground(makeHandleBackground(false));\n            modeHandle.setOnClickListener(v -> setEditMode(!editMode));\n\n            int w = dp(ctx, 36);\n            int h = dp(ctx, 48);\n            modeHandleParams = new WindowManager.LayoutParams(\n                    w, h,\n                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O\n                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY\n                            : WindowManager.LayoutParams.TYPE_PHONE,\n                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,\n                    PixelFormat.TRANSLUCENT);\n            modeHandleParams.gravity = Gravity.TOP | Gravity.START;\n            updateModeHandlePosition();\n            wm.addView(modeHandle, modeHandleParams);\n        } catch (Throwable e) {\n            XposedCompat.log(TAG + \" control handle unavailable: \" + e.getMessage());\n            modeHandle = null;\n            modeHandleParams = null;\n        }\n    }\n\n    private GradientDrawable makeHandleBackground(boolean active) {\n        GradientDrawable bg = new GradientDrawable();\n        bg.setColor(active ? 0xD9434A54 : 0x99313640);\n        bg.setCornerRadius(dp(appContext, 18));\n        bg.setStroke(dp(appContext, 1), 0x66FFFFFF);\n        return bg;\n    }\n\n    private void updateModeHandlePosition() {\n        if (modeHandleParams == null || params == null || appContext == null) return;\n        int screenW = appContext.getResources().getDisplayMetrics().widthPixels;\n        int screenH = appContext.getResources().getDisplayMetrics().heightPixels;\n        int hw = modeHandleParams.width;\n        int hh = modeHandleParams.height;\n        modeHandleParams.x = clampInt(params.x + params.width - hw / 2, 0, Math.max(0, screenW - hw));\n        modeHandleParams.y = clampInt(params.y + params.height / 2 - hh / 2, 0, Math.max(0, screenH - hh));\n        if (wm != null && modeHandle != null) {\n            try { wm.updateViewLayout(modeHandle, modeHandleParams); } catch (Throwable ignored) {}\n        }\n    }\n\n    private void hideModeHandle() {\n        try { if (wm != null && modeHandle != null) wm.removeView(modeHandle); } catch (Throwable ignored) {}\n        modeHandle = null;\n        modeHandleParams = null;\n        editMode = false;\n    }\n\n    private void hide() {\n        hideModeHandle();\n",
      "FloatingWindowManager interaction modes")
patch(p, "        } catch (Throwable e) {\n            lastFailMs = SystemClock.uptimeMillis();",
      "        } catch (Throwable e) {\n            BlackScreenLayer.getInstance().detach();\n            lastFailMs = SystemClock.uptimeMillis();",
      "FloatingWindowManager cleanup black screen on show failure")
patch(p, "    private void hide() {\n        hideModeHandle();\n",
      "    private void hide() {\n        hideModeHandle();\n        BlackScreenLayer.getInstance().detach();\n",
      "FloatingWindowManager cleanup black screen on hide")
patch(p, "            modeHandle.setOnClickListener(v -> setEditMode(!editMode));\n",
      "            modeHandle.setOnClickListener(v -> setEditMode(!editMode));\n            modeHandle.setOnLongClickListener(v -> {\n                BlackScreenLayer.getInstance().disable();\n                return true;\n            });\n",
      "FloatingWindowManager long press handle exits black screen")
patch(p, "        modeHandleParams.x = clampInt(params.x + params.width - hw / 2, 0, Math.max(0, screenW - hw));\n        modeHandleParams.y = clampInt(params.y + params.height / 2 - hh / 2, 0, Math.max(0, screenH - hh));\n",
      "        int gap = dp(appContext, 4);\n        int alignedX = clampInt(params.x + params.width - hw, 0, Math.max(0, screenW - hw));\n        int above = params.y - hh - gap;\n        int below = params.y + params.height + gap;\n        if (above >= 0) {\n            modeHandleParams.x = alignedX;\n            modeHandleParams.y = above;\n        } else if (below + hh <= screenH) {\n            modeHandleParams.x = alignedX;\n            modeHandleParams.y = below;\n        } else if (params.x + params.width + gap + hw <= screenW) {\n            modeHandleParams.x = params.x + params.width + gap;\n            modeHandleParams.y = clampInt(params.y, 0, Math.max(0, screenH - hh));\n        } else {\n            modeHandleParams.x = clampInt(params.x - hw - gap, 0, Math.max(0, screenW - hw));\n            modeHandleParams.y = clampInt(params.y, 0, Math.max(0, screenH - hh));\n        }\n",
      "FloatingWindowManager place handle outside subtitles")
patch(p, "        sLastY = p.y;\n    }\n",
      '        sLastY = p.y;\n        Context ctx = appContext;\n        if (ctx != null) {\n            try {\n                ctx.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).edit()\n                        .putInt("window_w", sLastW).putInt("window_h", sLastH)\n                        .putInt("window_x", sLastX).putInt("window_y", sLastY).apply();\n            } catch (Throwable ignored) {}\n        }\n    }\n\n    private static void loadPersistentGeometry(Context ctx) {\n        if (sGeometryLoaded || ctx == null) return;\n        sGeometryLoaded = true;\n        try {\n            android.content.SharedPreferences p = ctx.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE);\n            sLastW = p.getInt("window_w", 0);\n            sLastH = p.getInt("window_h", 0);\n            sLastX = p.getInt("window_x", Integer.MIN_VALUE);\n            sLastY = p.getInt("window_y", Integer.MIN_VALUE);\n        } catch (Throwable ignored) {}\n    }\n',
      "FloatingWindowManager persist geometry")
patch(p, "                    case MotionEvent.ACTION_DOWN: {\n                        downRawX = event.getRawX();\n",
      "                    case MotionEvent.ACTION_DOWN: {\n                        if (view.hitInteractiveControlArea(event.getX(), event.getY())) return false;\n                        downRawX = event.getRawX();\n",
      "FloatingWindowManager child controls")
patch(p, "                    case MotionEvent.ACTION_MOVE: {\n                        float rawDx = event.getRawX() - downRawX;\n",
      "                    case MotionEvent.ACTION_MOVE: {\n                        if (view.isWindowLocked()) return true;\n                        float rawDx = event.getRawX() - downRawX;\n",
      "FloatingWindowManager lock move")
patch(p, "                        try {\n                            wm.updateViewLayout(view, params);\n                        } catch (Throwable ignored) {\n                        }\n                        saveGeometry();",
      "                        try {\n                            wm.updateViewLayout(view, params);\n                        } catch (Throwable ignored) {\n                        }\n                        updateModeHandlePosition();\n                        saveGeometry();",
      "FloatingWindowManager track handle")

hook_dir = root / "app/src/main/java/io/github/ariinyume/dlsitesoundfloat/hook"
module_dir = root / "app/src/main/java/io/github/ariinyume/dlsitesoundfloat"

p = hook_dir / "NetworkHook.java"
patch(p, "import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;\n",
      "import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;\nimport io.github.ariinyume.dlsitesoundfloat.data.LocalLrcManager;\n",
      "NetworkHook local LRC import")
patch(p, "            repo.loadFromJson(text);\n",
      "            if (!LocalLrcManager.isOverrideActive()) repo.loadFromJson(text);\n",
      "NetworkHook local LRC precedence")

p = hook_dir / "PlayerSourceHook.java"
patch(p, "import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;\n",
      "import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;\nimport io.github.ariinyume.dlsitesoundfloat.data.LocalLrcManager;\n",
      "PlayerSourceHook local LRC import")
patch(p, "        repo.onTrackChanged(where);\n",
      "        LocalLrcManager.onTrackChanged();\n        repo.onTrackChanged(where);\n",
      "PlayerSourceHook local LRC reset")

p = hook_dir / "PlayerPositionHook.java"
patch(p, "import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;\n",
      "import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;\nimport io.github.ariinyume.dlsitesoundfloat.data.LocalLrcManager;\n",
      "PlayerPositionHook local LRC import")
patch(p, "                        PlayerControlBridge.observePlayer(chain.getThisObject());\n",
      "                        PlayerControlBridge.observePlayer(chain.getThisObject());\n                        LocalLrcManager.maybeRestore(ActivityButtonHook.currentActivity());\n",
      "PlayerPositionHook local LRC restore")

p = hook_dir / "ActivityButtonHook.java"
patch(p, "import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;\n",
      "import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;\nimport io.github.ariinyume.dlsitesoundfloat.data.LocalLrcManager;\n",
      "ActivityButtonHook local LRC import")
patch(p, "    public static void hook(ClassLoader cl, SubtitleRepository repo) {\n",
      "    public static Activity currentActivity() { return sActivity; }\n\n    public static void hook(ClassLoader cl, SubtitleRepository repo) {\n",
      "ActivityButtonHook current activity for local picker")
patch(p, "        sButton.setOnClickListener(v -> {\n",
      "        sButton.setOnLongClickListener(v -> { LocalLrcManager.pickCurrentTrack(); return true; });\n        sButton.setOnClickListener(v -> {\n",
      "ActivityButtonHook long press import for tracks without built-in subtitles")

p = module_dir / "DlsiteSoundSubtitleModule.java"
patch(p, "import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;\n",
      "import io.github.ariinyume.dlsitesoundfloat.data.SubtitleRepository;\nimport io.github.ariinyume.dlsitesoundfloat.data.LocalLrcManager;\n",
      "module local LRC import")
patch(p, "        ActivityButtonHook.hook(cl, repo);\n",
      "        ActivityButtonHook.hook(cl, repo);\n        LocalLrcManager.hook(cl);\n",
      "module local LRC hook")

print("DLsiteFloat Rootless Controls v4 and local LRC patches applied")
