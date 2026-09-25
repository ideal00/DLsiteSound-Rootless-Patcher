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
patch(p, "        closeBtn.setOnClickListener(v -> {\n",
      "        playbackControls = new PlaybackControlsView(getContext());\n        FrameLayout.LayoutParams controlsLp = new FrameLayout.LayoutParams(\n                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);\n        controlsLp.gravity = Gravity.BOTTOM;\n        controlsLp.leftMargin = dp(10);\n        controlsLp.rightMargin = dp(46);\n        controlsLp.bottomMargin = dp(10);\n        playbackControls.setLayoutParams(controlsLp);\n\n        closeBtn.setOnClickListener(v -> {\n",
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
