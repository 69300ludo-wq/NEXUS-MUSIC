package com.nexusmusic.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public final class NexusMusicView extends ScrollView {
    public interface Actions {
        void onPickLocalAudio();
        void onTogglePlayPause();
        void onSeekTo(float progress);
        void onBrowseRadios();
        void onAddRadio();
    }

    private static final int BG=0xFF02040A;
    private static final int PANEL=0xFF081426;
    private static final int CYAN=0xFF63F7FF;
    private static final int VIOLET=0xFFAD63FF;
    private static final int WHITE=0xFFF1FBFF;
    private static final int MUTED=0xFF8CA5BA;

    private Actions actions;
    private final TextView title;
    private final TextView subtitle;
    private final TextView status;
    private final Button play;
    private final SeekBar seek;
    private final TextView time;

    public NexusMusicView(Context c) {
        super(c);
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setBackgroundColor(BG);

        LinearLayout root=new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18),dp(22),dp(18),dp(26));
        addView(root,new LayoutParams(-1,-2));

        TextView logo=text("NEXUS MUSIC",30,WHITE,true);
        logo.setGravity(Gravity.CENTER);
        logo.setShadowLayer(dp(14),0,0,CYAN);
        root.addView(logo,new LinearLayout.LayoutParams(-1,dp(48)));

        TextView edition=text("FINAL 3.0 // NATIVE AUDIO CORE",9,CYAN,true);
        edition.setGravity(Gravity.CENTER);
        root.addView(edition,new LinearLayout.LayoutParams(-1,dp(26)));

        TextView core=text("◉  NEXUS CORE ONLINE",11,CYAN,true);
        core.setGravity(Gravity.CENTER);
        core.setBackground(panel(CYAN));
        root.addView(core,lp(dp(48),dp(4),dp(14)));

        title=text("Aucune musique",21,WHITE,true);
        title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(40)));

        subtitle=text("Choisis une musique ou ouvre la radio",10,MUTED,false);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(28)));

        status=text("PRÊT",11,CYAN,true);
        status.setGravity(Gravity.CENTER);
        status.setBackground(panel(VIOLET));
        root.addView(status,lp(dp(44),dp(4),dp(12)));

        Button choose=button("＋  CHOISIR UNE MUSIQUE",CYAN);
        choose.setOnClickListener(v->{if(actions!=null)actions.onPickLocalAudio();});
        root.addView(choose,lp(dp(56),0,dp(8)));

        play=button("▶  PLAY / PAUSE",VIOLET);
        play.setOnClickListener(v->{if(actions!=null)actions.onTogglePlayPause();});
        root.addView(play,lp(dp(56),0,dp(6)));

        seek=new SeekBar(c);
        seek.setMax(1000);
        seek.setProgressTintList(ColorStateList.valueOf(CYAN));
        seek.setThumbTintList(ColorStateList.valueOf(VIOLET));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            boolean user;
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){user=fromUser;}
            @Override public void onStartTrackingTouch(SeekBar bar){user=true;}
            @Override public void onStopTrackingTouch(SeekBar bar){
                if(user&&actions!=null)actions.onSeekTo(bar.getProgress()/1000f);
                user=false;
            }
        });
        root.addView(seek,lp(dp(38),0,0));

        time=text("0:00 / 0:00",9,MUTED,true);
        time.setGravity(Gravity.CENTER);
        root.addView(time,new LinearLayout.LayoutParams(-1,dp(30)));

        TextView radio=text("RADIO // WORLD SIGNAL",15,WHITE,true);
        radio.setGravity(Gravity.CENTER);
        root.addView(radio,lp(dp(42),dp(10),dp(8)));

        Button browse=button("◎  RECHERCHER UNE RADIO",CYAN);
        browse.setOnClickListener(v->{if(actions!=null)actions.onBrowseRadios();});
        root.addView(browse,lp(dp(56),0,dp(8)));

        Button direct=button("＋  URL RADIO DIRECTE",VIOLET);
        direct.setOnClickListener(v->{if(actions!=null)actions.onAddRadio();});
        root.addView(direct,lp(dp(56),0,dp(16)));

        LinearLayout info=new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14),dp(12),dp(14),dp(12));
        info.setBackground(panel(CYAN));
        info.addView(text("AUDIO ENGINE",9,CYAN,true),new LinearLayout.LayoutParams(-1,dp(22)));
        info.addView(text("Android MediaPlayer natif • focus audio • pause casque débranché",9,WHITE,false),
                new LinearLayout.LayoutParams(-1,dp(34)));
        info.addView(text("Lecture locale + radio HTTP/HTTPS • aucun compte • aucune publicité",9,MUTED,false),
                new LinearLayout.LayoutParams(-1,dp(34)));
        root.addView(info,new LinearLayout.LayoutParams(-1,dp(102)));

        TextView footer=text("NEXUS MUSIC 3.0 FINAL",8,MUTED,true);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(42)));
    }

    public void setActions(Actions a){actions=a;}
    public void setStatus(String s){status.setText(s==null?"":s);}
    public void setTitleText(String a,String b){
        title.setText(a==null?"":a);
        subtitle.setText(b==null?"":b);
    }
    public void setPlaying(boolean v){
        play.setText(v?"Ⅱ  PAUSE":"▶  PLAY / PAUSE");
    }
    public void setProgress(float p,long pos,long dur){
        int value=Math.round(Math.max(0f,Math.min(1f,p))*1000f);
        if(seek.getProgress()!=value)seek.setProgress(value);
        time.setText(format(pos)+" / "+format(dur));
    }

    private Button button(String label,int accent){
        Button b=new Button(getContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setTextSize(11);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        b.setGravity(Gravity.CENTER);
        b.setBackground(panel(accent));
        return b;
    }

    private TextView text(String s,float size,int color,boolean bold){
        TextView v=new TextView(getContext());
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        return v;
    }

    private GradientDrawable panel(int accent){
        GradientDrawable d=new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{PANEL,0xFF10152B});
        d.setCornerRadius(dp(16));
        d.setStroke(dp(1),accent);
        return d;
    }

    private LinearLayout.LayoutParams lp(int h,int top,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(h));
        p.setMargins(0,dp(top),0,dp(bottom));
        return p;
    }

    private int dp(float v){
        return Math.round(v*getResources().getDisplayMetrics().density);
    }

    private static String format(long ms){
        if(ms<=0)return "0:00";
        long s=ms/1000;
        return String.format(Locale.US,"%d:%02d",s/60,s%60);
    }
}
