/*
 * DLsiteSound Rootless Patcher - floating player enhancements
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.ariinyume.dlsitesoundfloat.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

import io.github.ariinyume.dlsitesoundfloat.control.PlayerControlBridge;

public final class PlaybackControlsView extends LinearLayout {
    public static final String PREFS = "dlsitefloat_overlay_ui";
    public static final String PREF_LOCKED = "window_locked";
    public static final String PREF_PANEL_OPACITY = "panel_opacity_pct";
    public static final String PREF_CONTROL_OPACITY = "control_opacity_pct";
    public static final String PREF_FONT_SIZE_SP = "subtitle_font_size_sp";

    public static final int DEFAULT_PANEL_OPACITY = 100;
    public static final int DEFAULT_CONTROL_OPACITY = 90;
    public static final int DEFAULT_FONT_SIZE_SP = 17;

    private static final int MIN_OPACITY = 20;
    private static final int MIN_FONT_SIZE_SP = 13;
    private static final int MAX_FONT_SIZE_SP = 24;
    private static final long AUTO_HIDE_MS = 5000L;
    private static final int SEEK_MAX = 1000;

    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable appearanceChanged;
    private final SeekBar seekBar;
    private final TextView timeText;
    private final TextView playPause;
    private TextView lockButton;
    private final TextView settingsButton;
    private LinearLayout settingsPanel;

    private boolean userSeeking;
    private boolean adjustingSetting;
    private boolean locked;
    private int controlOpacityPct;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshPlaybackState();
            handler.postDelayed(this, 350L);
        }
    };

    private final Runnable autoHide = () -> {
        if (!userSeeking && !adjustingSetting && settingsPanel != null && settingsPanel.getVisibility() != VISIBLE) {
            setVisibility(GONE);
        }
    };

    public PlaybackControlsView(Context context, Runnable appearanceChanged) {
        super(context);
        this.appearanceChanged = appearanceChanged;
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        locked = prefs.getBoolean(PREF_LOCKED, false);
        controlOpacityPct = clamp(
                prefs.getInt(PREF_CONTROL_OPACITY, DEFAULT_CONTROL_OPACITY),
                MIN_OPACITY, 100);

        setOrientation(VERTICAL);
        setPadding(dp(12), dp(8), dp(12), dp(9));
        applyControlBackground();

        LinearLayout progressRow = new LinearLayout(context);
        progressRow.setOrientation(HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);

        timeText = label("00:00 / 00:00", 11);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        timeLp.rightMargin = dp(6);
        progressRow.addView(timeText, timeLp);

        seekBar = new SeekBar(context);
        seekBar.setMax(SEEK_MAX);
        seekBar.setPadding(0, 0, 0, 0);
        progressRow.addView(seekBar, new LinearLayout.LayoutParams(0, dp(32), 1f));
        addView(progressRow, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        lockButton = action(locked ? "🔒" : "🔓", () -> {
            locked = !locked;
            prefs.edit().putBoolean(PREF_LOCKED, locked).apply();
            lockButton.setText(locked ? "🔒" : "🔓");
            armAutoHide();
        });
        buttons.addView(lockButton, weighted());

        buttons.addView(action("−10", () -> {
            PlayerControlBridge.seekBy(-10_000L);
            armAutoHide();
        }), weighted());

        buttons.addView(action("⏮", () -> {
            PlayerControlBridge.previous();
            armAutoHide();
        }), weighted());

        playPause = action("▶", () -> {
            PlayerControlBridge.togglePlayPause();
            refreshPlaybackState();
            armAutoHide();
        });
        playPause.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
        buttons.addView(playPause, weighted());

        buttons.addView(action("⏭", () -> {
            PlayerControlBridge.next();
            armAutoHide();
        }), weighted());

        buttons.addView(action("+10", () -> {
            PlayerControlBridge.seekBy(10_000L);
            armAutoHide();
        }), weighted());

        settingsButton = action("⚙", this::toggleSettings);
        buttons.addView(settingsButton, weighted());

        addView(buttons, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(42)));

        settingsPanel = new LinearLayout(context);
        settingsPanel.setOrientation(VERTICAL);
        settingsPanel.setPadding(dp(2), dp(4), dp(2), 0);
        settingsPanel.setVisibility(GONE);

        addSettingRow(
                settingsPanel, "字幕面板", PREF_PANEL_OPACITY,
                MIN_OPACITY, 100, DEFAULT_PANEL_OPACITY, "%",
                value -> notifyAppearanceChanged());

        addSettingRow(
                settingsPanel, "控制栏", PREF_CONTROL_OPACITY,
                MIN_OPACITY, 100, DEFAULT_CONTROL_OPACITY, "%",
                value -> {
                    controlOpacityPct = value;
                    applyControlBackground();
                });

        addSettingRow(
                settingsPanel, "字幕字号", PREF_FONT_SIZE_SP,
                MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP, DEFAULT_FONT_SIZE_SP, "sp",
                value -> notifyAppearanceChanged());

        addView(settingsPanel, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                long duration = PlayerControlBridge.getDurationMs();
                long preview = duration > 0 ? duration * progress / SEEK_MAX : 0L;
                timeText.setText(formatTime(preview) + " / " + formatTime(duration));
                armAutoHide();
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
                cancelAutoHide();
            }

            @Override public void onStopTrackingTouch(SeekBar bar) {
                long duration = PlayerControlBridge.getDurationMs();
                if (duration > 0) {
                    PlayerControlBridge.seekTo(duration * bar.getProgress() / SEEK_MAX);
                }
                userSeeking = false;
                armAutoHide();
            }
        });

        setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                armAutoHide();
            }
            return false;
        });

        setVisibility(GONE);
    }

    private interface IntSettingListener {
        void onChanged(int value);
    }

    private void addSettingRow(
            LinearLayout parent,
            String title,
            String key,
            int min,
            int max,
            int defaultValue,
            String suffix,
            IntSettingListener listener) {

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = label(title, 11);
        titleView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.addView(titleView, new LinearLayout.LayoutParams(dp(62), dp(34)));

        TextView valueView = label("", 11);
        valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        SeekBar slider = new SeekBar(getContext());
        slider.setMax(max - min);
        int current = clamp(prefs.getInt(key, defaultValue), min, max);
        slider.setProgress(current - min);
        valueView.setText(current + suffix);

        row.addView(slider, new LinearLayout.LayoutParams(0, dp(34), 1f));
        row.addView(valueView, new LinearLayout.LayoutParams(dp(48), dp(34)));

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int value = min + progress;
                valueView.setText(value + suffix);
                prefs.edit().putInt(key, value).apply();
                listener.onChanged(value);
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {
                adjustingSetting = true;
                cancelAutoHide();
            }

            @Override public void onStopTrackingTouch(SeekBar bar) {
                adjustingSetting = false;
                cancelAutoHide();
            }
        });

        parent.addView(row, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void notifyAppearanceChanged() {
        if (appearanceChanged != null) appearanceChanged.run();
    }

    private void toggleSettings() {
        boolean opening = settingsPanel.getVisibility() != VISIBLE;
        settingsPanel.setVisibility(opening ? VISIBLE : GONE);
        settingsButton.setText(opening ? "✓" : "⚙");
        if (opening) cancelAutoHide(); else armAutoHide();
    }

    private void applyControlBackground() {
        int alpha = Math.round(255f * clamp(controlOpacityPct, MIN_OPACITY, 100) / 100f);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor((alpha << 24) | 0x001C1D21);
        bg.setCornerRadius(dp(18));
        setBackground(bg);
        setElevation(dp(8));
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
    }

    private TextView label(String text, float sp) {
        TextView v = new TextView(getContext());
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        return v;
    }

    private TextView action(String text, Runnable action) {
        TextView v = label(text, 15);
        v.setPadding(dp(3), 0, dp(3), 0);
        v.setBackgroundColor(Color.TRANSPARENT);
        v.setClickable(true);
        v.setFocusable(true);
        v.setOnClickListener(x -> action.run());
        return v;
    }

    public boolean isWindowLocked() {
        return locked;
    }

    public boolean containsPoint(float x, float y) {
        return getVisibility() == VISIBLE
                && x >= getLeft() && x <= getRight()
                && y >= getTop() && y <= getBottom();
    }

    public void toggleControls() {
        if (getVisibility() == VISIBLE) {
            if (settingsPanel.getVisibility() == VISIBLE) {
                settingsPanel.setVisibility(GONE);
                settingsButton.setText("⚙");
                armAutoHide();
            } else {
                hideControls();
            }
        } else {
            showControls();
        }
    }

    public void showControls() {
        setVisibility(VISIBLE);
        refreshPlaybackState();
        armAutoHide();
    }

    public void hideControls() {
        cancelAutoHide();
        settingsPanel.setVisibility(GONE);
        settingsButton.setText("⚙");
        setVisibility(GONE);
    }

    private void cancelAutoHide() {
        handler.removeCallbacks(autoHide);
    }

    private void armAutoHide() {
        cancelAutoHide();
        if (settingsPanel.getVisibility() != VISIBLE && !adjustingSetting && !userSeeking) {
            handler.postDelayed(autoHide, AUTO_HIDE_MS);
        }
    }

    private void refreshPlaybackState() {
        if (getVisibility() != VISIBLE) return;
        playPause.setText(PlayerControlBridge.isPlaying() ? "Ⅱ" : "▶");
        long pos = PlayerControlBridge.getCurrentPositionMs();
        long duration = PlayerControlBridge.getDurationMs();
        if (!userSeeking) {
            int progress = duration > 0
                    ? (int) Math.max(0, Math.min(SEEK_MAX, pos * SEEK_MAX / duration))
                    : 0;
            seekBar.setProgress(progress);
            timeText.setText(formatTime(pos) + " / " + formatTime(duration));
        }
        seekBar.setEnabled(PlayerControlBridge.hasPlayer() && duration > 0);
    }

    private static String formatTime(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%02d:%02d", m, s);
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(ticker);
        cancelAutoHide();
        super.onDetachedFromWindow();
    }
}
