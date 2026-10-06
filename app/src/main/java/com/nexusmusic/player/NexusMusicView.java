package com.nexusmusic.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public final class NexusMusicView extends ScrollView {
    public interface Actions {
        void onPickLocalAudio();
        void onBrowseRadios();
        void onTogglePlayPause();
        void onSeekTo(float progress);
        void onAddRadio();
        void onResumeLastRadio();
        boolean hasSavedRadio();
        void onTestAudio();
    }

    private static final int BG = 0xFF02040A;
    private static final int PANEL = 0xFF0A1222;
    private static final int CYAN = 0xFF63F7FF;
    private static final int VIOLET = 0xFFAD63FF;
    private static final int WHITE = 0xFFF1FBFF;
    private static final int MUTED = 0xFF8CA5BA;
    private static final int DANGER = 0xFFFF6B8A;

    private Actions actions;
    private final TextView title;
    private final TextView subtitle;
    private final TextView status;
    private final TextView log;
    private final Button play;
    private final Button resume;
    private final SeekBar seek;
    private final TextView time;

    public NexusMusicView(Context context) {
        super(context);
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(24));
        addView(root, new LayoutParams(-1, -2));

        TextView logo = text("NEXUS MUSIC", 28, WHITE, true);
        logo.setShadowLayer(dp(14), 0, 0, CYAN);
        root.addView(logo, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView version = text("CORE RECOVERY 2.0 // ANDROID NATIVE AUDIO", 9, CYAN, true);
        root.addView(version, new LinearLayout.LayoutParams(-1, dp(24)));

        TextView core = text("NEXUS CORE", 13, CYAN, true);
        core.setGravity(Gravity.CENTER);
        core.setBackground(panel(CYAN));
        root.addView(core, lp(dp(58), 0, dp(12)));

        title = text("Aucun média chargé", 20, WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(38)));

        subtitle = text("Commence par TEST AUDIO INTERNE", 10, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(30)));

        status = text("PRÊT", 11, CYAN, true);
        status.setGravity(Gravity.CENTER);
        status.setBackground(panel(VIOLET));
        root.addView(status, lp(dp(46), dp(2), dp(12)));

        Button test = button("1  •  TEST AUDIO INTERNE", CYAN);
        test.setOnClickListener(v -> {
            if (actions != null) actions.onTestAudio();
        });
        root.addView(test, lp(dp(54), dp(6), dp(4)));

        Button choose = button("2  •  CHOISIR UNE MUSIQUE", VIOLET);
        choose.setOnClickListener(v -> {
            if (actions != null) actions.onPickLocalAudio();
        });
        root.addView(choose, lp(dp(54), dp(4), dp(4)));

        LinearLayout transport = row();
        transport.setBackground(panel(CYAN));

        play = button("▶  PLAY / PAUSE", CYAN);
        play.setOnClickListener(v -> {
            if (actions != null) actions.onTogglePlayPause();
        });
        transport.addView(play, new LinearLayout.LayoutParams(0, dp(50), 1f));

        time = text("0:00 / 0:00", 9, MUTED, true);
        time.setGravity(Gravity.CENTER);
        transport.addView(time, new LinearLayout.LayoutParams(dp(100), dp(50)));

        root.addView(transport, lp(dp(58), dp(4), dp(2)));

        seek = new SeekBar(context);
        seek.setMax(1000);
        seek.setProgressTintList(ColorStateList.valueOf(CYAN));
        seek.setThumbTintList(ColorStateList.valueOf(VIOLET));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean user;
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                user = fromUser;
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {
                user = true;
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (user && actions != null) {
                    actions.onSeekTo(bar.getProgress() / 1000f);
                }
                user = false;
            }
        });
        root.addView(seek, lp(dp(42), 0, dp(10)));

        TextView radioTitle = text("RADIO", 16, WHITE, true);
        radioTitle.setGravity(Gravity.CENTER);
        root.addView(radioTitle, new LinearLayout.LayoutParams(-1, dp(34)));

        Button search = button("3  •  RECHERCHER UNE RADIO", CYAN);
        search.setOnClickListener(v -> {
            if (actions != null) actions.onBrowseRadios();
        });
        root.addView(search, lp(dp(52), dp(3), dp(4)));

        Button direct = button("4  •  URL RADIO DIRECTE", VIOLET);
        direct.setOnClickListener(v -> {
            if (actions != null) actions.onAddRadio();
        });
        root.addView(direct, lp(dp(52), dp(3), dp(4)));

        resume = button("5  •  DERNIÈRE RADIO", CYAN);
        resume.setOnClickListener(v -> {
            if (actions != null) actions.onResumeLastRadio();
        });
        root.addView(resume, lp(dp(52), dp(3), dp(10)));

        TextView diagTitle = text("DIAGNOSTIC EN DIRECT", 13, DANGER, true);
        root.addView(diagTitle, new LinearLayout.LayoutParams(-1, dp(30)));

        log = text("", 10, WHITE, false);
        log.setGravity(Gravity.TOP | Gravity.LEFT);
        log.setPadding(dp(10), dp(10), dp(10), dp(10));
        log.setBackground(panel(DANGER));
        log.setMovementMethod(new ScrollingMovementMethod());
        root.addView(log, new LinearLayout.LayoutParams(-1, dp(220)));

        TextView footer = text(
                "Aucun prénom affiché • aucun compte • aucun cloud • moteur Android natif",
                8, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(42)));
    }

    public void setActions(Actions value) {
        actions = value;
        resume.setText(value != null && value.hasSavedRadio()
                ? "5  •  REPRENDRE LA DERNIÈRE RADIO"
                : "5  •  AUCUNE RADIO ENREGISTRÉE");
    }

    public void setTitleText(String main, String sub) {
        title.setText(main == null ? "" : main);
        subtitle.setText(sub == null ? "" : sub);
    }

    public void setStatus(String value) {
        status.setText(value == null ? "" : value);
    }

    public void setPlaying(boolean value) {
        play.setText(value ? "Ⅱ  PAUSE" : "▶  PLAY / PAUSE");
    }

    public void setProgress(float fraction, long positionMs, long durationMs) {
        int value = Math.round(Math.max(0f, Math.min(1f, fraction)) * 1000f);
        if (seek.getProgress() != value) seek.setProgress(value);
        time.setText(format(positionMs) + " / " + format(durationMs));
    }

    public void appendLog(String message) {
        if (message == null || message.isEmpty()) return;
        String old = log.getText().toString();
        String next = old.isEmpty() ? "• " + message : old + "\n• " + message;
        if (next.length() > 6000) next = next.substring(next.length() - 6000);
        log.setText(next);
        log.post(() -> {
            int bottom = log.getLayout() == null ? 0 : log.getLayout().getLineTop(log.getLineCount());
            int scroll = bottom - log.getHeight();
            if (scroll > 0) log.scrollTo(0, scroll);
        });
    }

    private Button button(String label, int accent) {
        Button b = new Button(getContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setTextSize(10);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setGravity(Gravity.CENTER);
        b.setBackground(panel(accent));
        return b;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(getContext());
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return v;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(getContext());
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private GradientDrawable panel(int accent) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{PANEL, 0xFF10152B});
        d.setCornerRadius(dp(16));
        d.setStroke(dp(1), accent);
        return d;
    }

    private LinearLayout.LayoutParams lp(int height, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(height));
        p.setMargins(0, dp(top), 0, dp(bottom));
        return p;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String format(long ms) {
        if (ms <= 0) return "0:00";
        long seconds = ms / 1000;
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }
}
