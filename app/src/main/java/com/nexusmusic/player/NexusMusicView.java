package com.nexusmusic.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public final class NexusMusicView extends ScrollView {
    public interface Actions {
        void onTestSpeaker();
        void onPickLocalAudio();
        void onTogglePlayPause();
        void onBrowseRadios();
        void onAddRadio();
        void onCopyDiagnostic();
    }

    private static final int BG=0xFF02040A;
    private static final int PANEL=0xFF0A1222;
    private static final int CYAN=0xFF63F7FF;
    private static final int VIOLET=0xFFAD63FF;
    private static final int WHITE=0xFFF1FBFF;
    private static final int MUTED=0xFF8CA5BA;
    private static final int RED=0xFFFF6B8A;

    private Actions actions;
    private final TextView title;
    private final TextView subtitle;
    private final TextView status;
    private final TextView log;
    private final Button play;
    private final SeekBar seek;
    private final TextView time;

    public NexusMusicView(Context c) {
        super(c);
        setFillViewport(true);
        setVerticalScrollBarEnabled(true);
        setBackgroundColor(BG);

        LinearLayout root=new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(18),dp(16),dp(26));
        addView(root,new LayoutParams(-1,-2));

        TextView logo=text("NEXUS MUSIC",28,WHITE,true);
        logo.setShadowLayer(dp(12),0,0,CYAN);
        root.addView(logo,new LinearLayout.LayoutParams(-1,dp(42)));

        TextView mode=text("SAFE MODE 2.1 // DIAGNOSTIC TÉLÉPHONE",10,CYAN,true);
        root.addView(mode,new LinearLayout.LayoutParams(-1,dp(28)));

        status=text("PRÊT",12,CYAN,true);
        status.setGravity(Gravity.CENTER);
        status.setBackground(panel(VIOLET));
        root.addView(status,lp(dp(46),dp(2),dp(10)));

        Button beep=button("1  •  TEST BIP ANDROID",CYAN);
        beep.setOnClickListener(v->{ if(actions!=null) actions.onTestSpeaker(); });
        root.addView(beep,lp(dp(54),0,dp(6)));

        Button file=button("2  •  CHOISIR UNE MUSIQUE",VIOLET);
        file.setOnClickListener(v->{ if(actions!=null) actions.onPickLocalAudio(); });
        root.addView(file,lp(dp(54),0,dp(10)));

        title=text("Aucun média chargé",19,WHITE,true);
        title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(36)));

        subtitle=text("Commence par le TEST BIP ANDROID",10,MUTED,false);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(28)));

        play=button("▶  PLAY / PAUSE",CYAN);
        play.setOnClickListener(v->{ if(actions!=null) actions.onTogglePlayPause(); });
        root.addView(play,lp(dp(52),dp(2),dp(4)));

        seek=new SeekBar(c);
        seek.setMax(1000);
        seek.setEnabled(false);
        seek.setProgressTintList(ColorStateList.valueOf(CYAN));
        seek.setThumbTintList(ColorStateList.valueOf(VIOLET));
        root.addView(seek,lp(dp(36),0,0));

        time=text("0:00 / 0:00",9,MUTED,true);
        time.setGravity(Gravity.CENTER);
        root.addView(time,new LinearLayout.LayoutParams(-1,dp(28)));

        TextView radio=text("RADIO",16,WHITE,true);
        radio.setGravity(Gravity.CENTER);
        root.addView(radio,new LinearLayout.LayoutParams(-1,dp(36)));

        Button search=button("3  •  RECHERCHER UNE RADIO",CYAN);
        search.setOnClickListener(v->{ if(actions!=null) actions.onBrowseRadios(); });
        root.addView(search,lp(dp(52),0,dp(6)));

        Button direct=button("4  •  URL RADIO DIRECTE",VIOLET);
        direct.setOnClickListener(v->{ if(actions!=null) actions.onAddRadio(); });
        root.addView(direct,lp(dp(52),0,dp(12)));

        TextView diag=text("DIAGNOSTIC EN DIRECT",14,RED,true);
        root.addView(diag,new LinearLayout.LayoutParams(-1,dp(32)));

        log=text("",10,WHITE,false);
        log.setGravity(Gravity.TOP|Gravity.LEFT);
        log.setPadding(dp(10),dp(10),dp(10),dp(10));
        log.setBackground(panel(RED));
        log.setMovementMethod(new ScrollingMovementMethod());
        root.addView(log,new LinearLayout.LayoutParams(-1,dp(210)));

        Button copy=button("COPIER LE DIAGNOSTIC",RED);
        copy.setOnClickListener(v->{ if(actions!=null) actions.onCopyDiagnostic(); });
        root.addView(copy,lp(dp(52),dp(8),dp(8)));

        TextView footer=text(
                "Aucun prénom • aucun compte • aucun cloud • audio Android natif",
                8,MUTED,false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(40)));
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
        seek.setProgress(Math.round(Math.max(0f,Math.min(1f,p))*1000f));
        time.setText(format(pos)+" / "+format(dur));
    }
    public void appendLog(String message){
        if(message==null||message.isEmpty())return;
        String old=log.getText().toString();
        String next=(old.isEmpty()?"":"\n")+ "• "+message;
        String all=old+next;
        if(all.length()>7000) all=all.substring(all.length()-7000);
        log.setText(all);
    }
    public String getDiagnosticText(){
        return "NEXUS MUSIC SAFE MODE 2.1\nStatus: "+status.getText()+"\n"
                +"Titre: "+title.getText()+"\n"
                +"Sous-titre: "+subtitle.getText()+"\n\n"
                +log.getText();
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
        d.setCornerRadius(dp(14));
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
