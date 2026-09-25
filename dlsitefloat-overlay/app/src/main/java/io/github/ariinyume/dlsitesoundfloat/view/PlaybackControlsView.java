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
    private static final String PREFS = "dlsitefloat_overlay_ui";
    private static final String PREF_LOCKED = "window_locked";
    private static final long AUTO_HIDE_MS = 5000L;
    private static final int SEEK_MAX = 1000;

    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SeekBar seekBar;
    private final TextView timeText;
    private final TextView playPause;
    private final TextView lockButton;
    private boolean userSeeking;
    private boolean locked;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshPlaybackState();
            handler.postDelayed(this, 350L);
        }
    };
    private final Runnable autoHide = () -> setVisibility(GONE);

    public PlaybackControlsView(Context context) {
        super(context);
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        locked = prefs.getBoolean(PREF_LOCKED, false);
        setOrientation(VERTICAL);
        setPadding(dp(12), dp(8), dp(12), dp(9));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE61C1D21);
        bg.setCornerRadius(dp(18));
        setBackground(bg);
        setElevation(dp(8));

        LinearLayout progressRow = new LinearLayout(context);
        progressRow.setOrientation(HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);

        timeText = label("00:00 / 00:00", 11);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        timeLp.rightMargin = dp(6);
        progressRow.addView(timeText, timeLp);

        seekBar = new SeekBar(context);
        seekBar.setMax(SEEK_MAX);
        seekBar.setPadding(0, 0, 0, 0);
        progressRow.addView(seekBar, new LinearLayout.LayoutParams(0, dp(32), 1f));
        addView(progressRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

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
        buttons.addView(action("−10", () -> { PlayerControlBridge.seekBy(-10_000L); armAutoHide(); }), weighted());
        buttons.addView(action("⏮", () -> { PlayerControlBridge.previous(); armAutoHide(); }), weighted());

        playPause = action("▶", () -> {
            PlayerControlBridge.togglePlayPause();
            refreshPlaybackState();
            armAutoHide();
        });
        playPause.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
        buttons.addView(playPause, weighted());

        buttons.addView(action("⏭", () -> { PlayerControlBridge.next(); armAutoHide(); }), weighted());
        buttons.addView(action("+10", () -> { PlayerControlBridge.seekBy(10_000L); armAutoHide(); }), weighted());
        addView(buttons, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(42)));

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
                armAutoHide();
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                long duration = PlayerControlBridge.getDurationMs();
                if (duration > 0) PlayerControlBridge.seekTo(duration * bar.getProgress() / SEEK_MAX);
                userSeeking = false;
                armAutoHide();
            }
        });

        setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) armAutoHide();
            return false;
        });
        setVisibility(GONE);
    }

    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f); }

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
        v.setPadding(dp(4), 0, dp(4), 0);
        v.setBackgroundColor(Color.TRANSPARENT);
        v.setClickable(true);
        v.setFocusable(true);
        v.setOnClickListener(x -> action.run());
        return v;
    }

    public boolean isWindowLocked() { return locked; }

    public boolean containsPoint(float x, float y) {
        return getVisibility() == VISIBLE && x >= getLeft() && x <= getRight() && y >= getTop() && y <= getBottom();
    }

    public void toggleControls() {
        if (getVisibility() == VISIBLE) hideControls(); else showControls();
    }

    public void showControls() {
        setVisibility(VISIBLE);
        refreshPlaybackState();
        armAutoHide();
    }

    public void hideControls() {
        handler.removeCallbacks(autoHide);
        setVisibility(GONE);
    }

    private void armAutoHide() {
        handler.removeCallbacks(autoHide);
        handler.postDelayed(autoHide, AUTO_HIDE_MS);
    }

    private void refreshPlaybackState() {
        if (getVisibility() != VISIBLE) return;
        playPause.setText(PlayerControlBridge.isPlaying() ? "Ⅱ" : "▶");
        long pos = PlayerControlBridge.getCurrentPositionMs();
        long duration = PlayerControlBridge.getDurationMs();
        if (!userSeeking) {
            int progress = duration > 0 ? (int)Math.max(0, Math.min(SEEK_MAX, pos * SEEK_MAX / duration)) : 0;
            seekBar.setProgress(progress);
            timeText.setText(formatTime(pos) + " / " + formatTime(duration));
        }
        seekBar.setEnabled(PlayerControlBridge.hasPlayer() && duration > 0);
    }

    private static String formatTime(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        long h = total / 3600L, m = (total % 3600L) / 60L, s = total % 60L;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%02d:%02d", m, s);
    }

    private int dp(float value) {
        return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(autoHide);
        super.onDetachedFromWindow();
    }
}
