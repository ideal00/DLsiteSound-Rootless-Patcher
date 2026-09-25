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
patch(p, "    private CloseButtonView closeBtn;\n",
      "    private CloseButtonView closeBtn;\n    private PlaybackControlsView playbackControls;\n",
      "FloatingSubtitleView controls field")
patch(p, "    public void onPanelTapped() {\n        if (closeBtn == null) {\n",
      "    public void onPanelTapped() {\n        if (playbackControls != null) { playbackControls.toggleControls(); }\n        if (closeBtn == null) {\n",
      "FloatingSubtitleView panel tap")
patch(p, "        panel.setAlpha(blurActive ? 242 : 255);\n",
      "        int opacityPct = getContext().getSharedPreferences(PlaybackControlsView.PREFS, Context.MODE_PRIVATE)\n                .getInt(PlaybackControlsView.PREF_PANEL_OPACITY, PlaybackControlsView.DEFAULT_PANEL_OPACITY);\n        opacityPct = Math.max(20, Math.min(100, opacityPct));\n        panel.setAlpha(Math.round(255f * opacityPct / 100f));\n",
      "FloatingSubtitleView panel opacity")
patch(p, "        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);\n",
      "        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(12f, getSubtitleFontSizeSp() - 3f));\n",
      "FloatingSubtitleView hint font size")
patch(p, "            styleLine(tv, 18f, 0xFFFFFFFF, 1.0f, false);\n",
      "            styleLine(tv, getSubtitleFontSizeSp(), 0xFFFFFFFF, 1.0f, false);\n",
      "FloatingSubtitleView mirrored font size")
patch(p, "                styleLine(tv, BASE_TEXT_SP * CURRENT_SCALE, 0xFFFFFFFF, 1.0f, true);\n",
      "                styleLine(tv, getSubtitleFontSizeSp() * CURRENT_SCALE, 0xFFFFFFFF, 1.0f, true);\n",
      "FloatingSubtitleView current font size")
patch(p, "                    styleLine(tv, 15f, 0x99FFFFFF, 0.35f, false);\n",
      "                    styleLine(tv, Math.max(11f, getSubtitleFontSizeSp() - 2f), 0x99FFFFFF, 0.35f, false);\n",
      "FloatingSubtitleView context font size")
patch(p, "                BASE_TEXT_SP * CURRENT_SCALE, getContext().getResources().getDisplayMetrics());\n",
      "                getSubtitleFontSizeSp() * CURRENT_SCALE, getContext().getResources().getDisplayMetrics());\n",
      "FloatingSubtitleView measurement font size")
patch(p, "    /** 重算并把当前字幕行滚动到悬浮窗垂直居中（缩放窗口后调用；v20：不带动画，避免拖拽时抖动）。 */\n",
      "    private float getSubtitleFontSizeSp() {\n        int value = getContext().getSharedPreferences(PlaybackControlsView.PREFS, Context.MODE_PRIVATE)\n                .getInt(PlaybackControlsView.PREF_FONT_SIZE_SP, PlaybackControlsView.DEFAULT_FONT_SIZE_SP);\n        return Math.max(13, Math.min(24, value));\n    }\n\n    private void applyAppearanceSettings() {\n        applyPanelBackground();\n        if (hint != null) {\n            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(12f, getSubtitleFontSizeSp() - 3f));\n        }\n        lastRenderKey = null;\n        updateFromRepository();\n        requestLayout();\n    }\n\n    /** 重算并把当前字幕行滚动到悬浮窗垂直居中（缩放窗口后调用；v20：不带动画，避免拖拽时抖动）。 */\n",
      "FloatingSubtitleView appearance helpers")
patch(p, "        closeBtn.setOnClickListener(v -> {\n",
      "        playbackControls = new PlaybackControlsView(getContext(), this::applyAppearanceSettings);\n        FrameLayout.LayoutParams controlsLp = new FrameLayout.LayoutParams(\n                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);\n        controlsLp.gravity = Gravity.BOTTOM;\n        controlsLp.leftMargin = dp(10);\n        controlsLp.rightMargin = dp(46);\n        controlsLp.bottomMargin = dp(10);\n        playbackControls.setLayoutParams(controlsLp);\n\n        closeBtn.setOnClickListener(v -> {\n",
      "FloatingSubtitleView create controls")
patch(p, "        addView(grip);\n        addView(closeBtn); // 最后添加，保证在最上层、可点击\n",
      "        addView(grip);\n        addView(playbackControls);\n        addView(closeBtn); // 最后添加，保证在最上层、可点击\n",
      "FloatingSubtitleView attach controls")
patch(p, "    @Override\n    protected void onDetachedFromWindow() {\n",
      "    public boolean hitInteractiveControlArea(float x, float y) {\n        if (playbackControls != null && playbackControls.containsPoint(x, y)) return true;\n        return closeBtn != null && closeBtn.getVisibility() == VISIBLE\n                && x >= closeBtn.getLeft() && x <= closeBtn.getRight()\n                && y >= closeBtn.getTop() && y <= closeBtn.getBottom();\n    }\n\n    public boolean isWindowLocked() {\n        return playbackControls != null && playbackControls.isWindowLocked();\n    }\n\n    @Override\n    protected void onDetachedFromWindow() {\n",
      "FloatingSubtitleView interaction helpers")

p = "app/src/main/java/io/github/ariinyume/dlsitesoundfloat/window/FloatingWindowManager.java"
patch(p, "    private static int sLastY = Integer.MIN_VALUE;\n",
      '    private static int sLastY = Integer.MIN_VALUE;\n    private static boolean sGeometryLoaded = false;\n    private static final String UI_PREFS = "dlsitefloat_overlay_ui";\n',
      "FloatingWindowManager geometry fields")
patch(p, "            int screenH = ctx.getResources().getDisplayMetrics().heightPixels;\n",
      "            int screenH = ctx.getResources().getDisplayMetrics().heightPixels;\n            loadPersistentGeometry(ctx);\n",
      "FloatingWindowManager load geometry")
patch(p, "        sLastY = p.y;\n    }\n",
      '        sLastY = p.y;\n        Context ctx = appContext;\n        if (ctx != null) {\n            try {\n                ctx.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).edit()\n                        .putInt("window_w", sLastW).putInt("window_h", sLastH)\n                        .putInt("window_x", sLastX).putInt("window_y", sLastY).apply();\n            } catch (Throwable ignored) {}\n        }\n    }\n\n    private static void loadPersistentGeometry(Context ctx) {\n        if (sGeometryLoaded || ctx == null) return;\n        sGeometryLoaded = true;\n        try {\n            android.content.SharedPreferences p = ctx.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE);\n            sLastW = p.getInt("window_w", 0);\n            sLastH = p.getInt("window_h", 0);\n            sLastX = p.getInt("window_x", Integer.MIN_VALUE);\n            sLastY = p.getInt("window_y", Integer.MIN_VALUE);\n        } catch (Throwable ignored) {}\n    }\n',
      "FloatingWindowManager persist geometry")
patch(p, "                    case MotionEvent.ACTION_DOWN: {\n                        downRawX = event.getRawX();\n",
      "                    case MotionEvent.ACTION_DOWN: {\n                        if (view.hitInteractiveControlArea(event.getX(), event.getY())) return false;\n                        downRawX = event.getRawX();\n",
      "FloatingWindowManager child controls")
patch(p, "                    case MotionEvent.ACTION_MOVE: {\n                        float rawDx = event.getRawX() - downRawX;\n",
      "                    case MotionEvent.ACTION_MOVE: {\n                        if (view.isWindowLocked()) return true;\n                        float rawDx = event.getRawX() - downRawX;\n",
      "FloatingWindowManager lock move")

print("DLsiteFloat playback-control patches applied")
