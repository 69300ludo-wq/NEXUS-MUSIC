package com.nexusmusic.player;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;

import java.util.Locale;

public final class NexusMusicView extends LinearLayout {
    public interface Actions {
        void onPickLocalAudio();
        void onBrowseRadios();
        void onTogglePlayPause();
        void onSeekTo(float progress);
        void onAddRadio();
        void onResumeLastRadio();
        boolean hasSavedRadio();
    }

    private enum Screen { HOME, RADIO, LAB }

    private static final int BG = 0xFF02040A;
    private static final int PANEL = 0xC9101830;
    private static final int CYAN = 0xFF5FF5FF;
    private static final int VIOLET = 0xFFAC5AFF;
    private static final int PINK = 0xFFF44EFF;
    private static final int WHITE = 0xFFECF9FF;
    private static final int MUTED = 0xFF7692AC;

    private Actions actions;
    private Screen screen = Screen.HOME;
    private boolean playing;
    private long positionMs;
    private long durationMs;

    private final OrbView orb;
    private final TextView title;
    private final TextView subtitle;
    private final TextView status;
    private final TextView signal;
    private final TextView route;
    private final TextView sectionTitle;
    private final TextView sectionInfo;
    private final Button primary;
    private final Button secondary;
    private final Button third;
    private final Button play;
    private final SeekBar seek;
    private final TextView time;

    public NexusMusicView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(BG);
        setPadding(dp(18), dp(16), dp(18), dp(12));

        LinearLayout header = row();
        TextView logo = text("NEXUS", 25, WHITE, true);
        logo.setShadowLayer(dp(12), 0, 0, CYAN);
        header.addView(logo, new LayoutParams(0, dp(44), 1f));
        TextView badge = text("NO ADS • LOCAL", 9, CYAN, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(card(0x223DF7FF, 0x663DF7FF, 14));
        header.addView(badge, new LayoutParams(dp(104), dp(32)));
        addView(header);

        TextView tag = text("MUSIC // QUANTUM AUDIO", 10, CYAN, false);
        addView(tag, new LayoutParams(-1, dp(24)));

        LinearLayout tabs = row();
        tabs.addView(tab("MUSIQUE", Screen.HOME), new LayoutParams(0, dp(42), 1f));
        tabs.addView(space(7), new LayoutParams(dp(7), 1));
        tabs.addView(tab("RADIO", Screen.RADIO), new LayoutParams(0, dp(42), 1f));
        tabs.addView(space(7), new LayoutParams(dp(7), 1));
        tabs.addView(tab("AUDIO LAB", Screen.LAB), new LayoutParams(0, dp(42), 1f));
        addView(tabs);

        orb = new OrbView(context);
        addView(orb, new LayoutParams(-1, dp(230)));

        sectionTitle = text("QUANTUM PLAYER", 12, CYAN, true);
        sectionTitle.setGravity(Gravity.CENTER);
        addView(sectionTitle, new LayoutParams(-1, dp(28)));

        sectionInfo = text("Lecture locale haute fidélité", 10, MUTED, false);
        sectionInfo.setGravity(Gravity.CENTER);
        addView(sectionInfo, new LayoutParams(-1, dp(28)));

        title = text("Aucun morceau", 19, WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        addView(title, new LayoutParams(-1, dp(32)));

        subtitle = text("Importe un fichier ou lance une radio", 10, CYAN, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setSingleLine(true);
        addView(subtitle, new LayoutParams(-1, dp(26)));

        LinearLayout diag = row();
        diag.setPadding(dp(12), dp(5), dp(12), dp(5));
        diag.setBackground(card(0x161C3150, 0x443DF7FF, 16));
        LinearLayout left = column();
        signal = text("Format en attente", 9, CYAN, false);
        status = text("AUDIO CORE READY", 8, WHITE, true);
        left.addView(signal);
        left.addView(status);
        diag.addView(left, new LayoutParams(0, dp(46), 1f));
        route = text("Android Audio", 8, MUTED, false);
        route.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        diag.addView(route, new LayoutParams(0, dp(46), 1f));
        addView(diag, new LayoutParams(-1, dp(56)));

        addView(space(8), new LayoutParams(1, dp(8)));

        primary = actionButton();
        secondary = actionButton();
        third = actionButton();
        primary.setOnClickListener(v -> {
            if (actions == null) return;
            if (screen == Screen.HOME) actions.onPickLocalAudio();
            else if (screen == Screen.RADIO) actions.onBrowseRadios();
        });
        secondary.setOnClickListener(v -> { if (actions != null && screen == Screen.RADIO) actions.onAddRadio(); });
        third.setOnClickListener(v -> { if (actions != null && screen == Screen.RADIO) actions.onResumeLastRadio(); });
        addView(primary, new LayoutParams(-1, dp(54)));
        addView(secondary, new LayoutParams(-1, dp(54)));
        addView(third, new LayoutParams(-1, dp(54)));

        addView(new Space(context), new LayoutParams(1, 0, 1f));

        LinearLayout player = column();
        player.setPadding(dp(12), dp(8), dp(12), dp(8));
        player.setBackground(card(PANEL, 0x553DF7FF, 22));
        seek = new SeekBar(context);
        seek.setMax(1000);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(VIOLET));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean fromUser;
            public void onProgressChanged(SeekBar s, int p, boolean f) { fromUser = f; }
            public void onStartTrackingTouch(SeekBar s) { fromUser = true; }
            public void onStopTrackingTouch(SeekBar s) {
                if (fromUser && actions != null) actions.onSeekTo(s.getProgress() / 1000f);
                fromUser = false;
            }
        });
        player.addView(seek, new LayoutParams(-1, dp(28)));

        LinearLayout controls = row();
        time = text("0:00   •   LIVE", 8, MUTED, false);
        time.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(time, new LayoutParams(0, dp(44), 1f));
        play = actionButton();
        play.setText("▶");
        play.setTextSize(20);
        play.setOnClickListener(v -> { if (actions != null) actions.onTogglePlayPause(); });
        controls.addView(play, new LayoutParams(dp(70), dp(44)));
        TextView engine = text("MEDIA3", 8, CYAN, true);
        engine.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        controls.addView(engine, new LayoutParams(0, dp(44), 1f));
        player.addView(controls);
        addView(player, new LayoutParams(-1, dp(84)));

        renderScreen();
    }

    public void setActions(Actions value) { actions = value; renderScreen(); }
    public void setPlaying(boolean value) { playing = value; play.setText(value ? "Ⅱ" : "▶"); orb.setActive(value); }
    public void setTrackTitle(String value) { if (value != null && !value.isEmpty()) title.setText(value); }
    public void setTrackSubtitle(String value) { subtitle.setText(value == null ? "" : value); }
    public void setStatus(String value) { status.setText(value == null ? "" : value); }
    public void setSignalDiagnostics(String source, String output) {
        signal.setText(source == null ? "Format en attente" : source);
        route.setText(output == null ? "Android Audio" : output);
    }
    public void setProgress(float p, long pos, long dur) {
        int next = Math.round(Math.max(0f, Math.min(1f, p)) * 1000f);
        String label = format(pos) + "   •   " + (dur > 0 ? format(dur) : "LIVE");
        if (positionMs == pos && durationMs == dur && seek.getProgress() == next
                && label.contentEquals(time.getText())) return;
        positionMs = pos;
        durationMs = dur;
        if (seek.getProgress() != next) seek.setProgress(next);
        if (!label.contentEquals(time.getText())) time.setText(label);
    }
    public void showHome() { screen = Screen.HOME; renderScreen(); }

    private void renderScreen() {
        boolean radio = screen == Screen.RADIO;
        boolean lab = screen == Screen.LAB;
        orb.setVisibility(lab ? GONE : VISIBLE);
        title.setVisibility(lab ? GONE : VISIBLE);
        subtitle.setVisibility(lab ? GONE : VISIBLE);
        sectionInfo.getLayoutParams().height = lab ? dp(186) : dp(28);
        sectionInfo.setGravity(lab ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.CENTER);
        sectionInfo.setTextSize(lab ? 11 : 10);
        sectionInfo.requestLayout();

        if (screen == Screen.HOME) {
            sectionTitle.setText("QUANTUM PLAYER");
            sectionInfo.setText("Lecture locale • signal transparent");
            primary.setText("+  IMPORTER UN FICHIER AUDIO");
            primary.setVisibility(VISIBLE);
            secondary.setVisibility(GONE);
            third.setVisibility(GONE);
        } else if (radio) {
            sectionTitle.setText("RADIO // LIVE SIGNAL");
            sectionInfo.setText("Annuaire mondial • MP3 • AAC • HLS");
            primary.setText("◎  EXPLORER LES RADIOS DU MONDE");
            secondary.setText("+  AJOUTER UNE URL DIRECTE");
            third.setText(actions != null && actions.hasSavedRadio()
                    ? "↻  REPRENDRE LA DERNIÈRE RADIO" : "AUCUNE RADIO ENREGISTRÉE");
            primary.setVisibility(VISIBLE);
            secondary.setVisibility(VISIBLE);
            third.setVisibility(VISIBLE);
        } else {
            sectionTitle.setText("NEXUS AUDIO LAB");
            sectionInfo.setText("PURE AUDIO  •  READY\n\nEQ PARAMÉTRIQUE 20 BANDES  •  ROADMAP\nCONVOLVER FIR  •  ROADMAP\nUSB DAC / HI-RES  •  ROADMAP\nDSD / BIT-PERFECT  •  ROADMAP");
            primary.setVisibility(GONE);
            secondary.setVisibility(GONE);
            third.setVisibility(GONE);
        }
    }

    private Button tab(String label, Screen target) {
        Button b = actionButton();
        b.setText(label);
        b.setTextSize(9);
        b.setOnClickListener(v -> { screen = target; renderScreen(); });
        return b;
    }

    private Button actionButton() {
        Button b = new Button(getContext());
        b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setTextSize(10);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(card(0x161C3150, 0x553DF7FF, 16));
        return b;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView v = new TextView(getContext());
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return v;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(getContext());
        l.setOrientation(HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(getContext());
        l.setOrientation(VERTICAL);
        return l;
    }

    private View space(int dp) {
        Space s = new Space(getContext());
        s.setMinimumWidth(dp(dp));
        return s;
    }

    private GradientDrawable card(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String format(long ms) {
        if (ms <= 0) return "0:00";
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }
}
