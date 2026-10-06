package com.nexusmusic.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;

import java.util.Locale;

public final class NexusMusicView extends FrameLayout {
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

    private static final int CYAN=0xFF63F7FF, BLUE=0xFF5188FF, VIOLET=0xFFAD63FF,
            PINK=0xFFF45AFF, WHITE=0xFFF1FBFF, MUTED=0xFF829AB2;

    private Actions actions;
    private Screen screen=Screen.HOME;
    private boolean playing;
    private long positionMs,durationMs;

    private final NexusHudBackdrop hud;
    private final OrbView orb;
    private final TextView coreLabel,title,subtitle,status,signal,route;
    private final TextView primary,secondary,third,play,time,sectionInfo;
    private final LinearLayout actionArea,labArea;
    private final SeekBar seek;

    public NexusMusicView(Context c){
        super(c);
        setMinimumHeight(dp(900));

        hud=new NexusHudBackdrop(c);
        addView(hud,new LayoutParams(-1,-1));

        LinearLayout root=column();
        root.setPadding(dp(14),dp(14),dp(14),dp(14));
        addView(root,new LayoutParams(-1,-2));

        LinearLayout head=row();
        LinearLayout brand=column();
        TextView logo=text("NEXUS",28,WHITE,true);
        logo.setShadowLayer(dp(16),0,0,CYAN);
        brand.addView(logo,new LinearLayout.LayoutParams(-1,dp(36)));
        brand.addView(text("MUSIC // HOLOGRAPHIC AUDIO SYSTEM",8,CYAN,false),
                new LinearLayout.LayoutParams(-1,dp(18)));
        head.addView(brand,new LinearLayout.LayoutParams(0,dp(58),1f));

        TextView hiRes=text("●  CORE ONLINE",8,CYAN,true);
        hiRes.setGravity(Gravity.CENTER);
        hiRes.setBackground(glass(CYAN,0x16173C48,16));
        head.addView(hiRes,new LinearLayout.LayoutParams(dp(112),dp(34)));
        root.addView(head);

        LinearLayout nav=row();
        nav.addView(tab("MUSIQUE",Screen.HOME),new LinearLayout.LayoutParams(0,dp(40),1f));
        nav.addView(gap(6),new LinearLayout.LayoutParams(dp(6),1));
        nav.addView(tab("RADIO",Screen.RADIO),new LinearLayout.LayoutParams(0,dp(40),1f));
        nav.addView(gap(6),new LinearLayout.LayoutParams(dp(6),1));
        nav.addView(tab("AUDIO LAB",Screen.LAB),new LinearLayout.LayoutParams(0,dp(40),1f));
        root.addView(nav);

        FrameLayout cockpit=new FrameLayout(c);
        cockpit.setMinimumHeight(dp(340));
        root.addView(cockpit,new LinearLayout.LayoutParams(-1,dp(340)));

        TextView leftHud=miniPanel("SOURCE\nAUTO DETECT",CYAN);
        FrameLayout.LayoutParams lpL=new FrameLayout.LayoutParams(dp(94),dp(58),Gravity.LEFT|Gravity.TOP);
        lpL.setMargins(dp(0),dp(58),0,0);
        cockpit.addView(leftHud,lpL);

        TextView rightHud=miniPanel("OUTPUT\nLIVE ROUTE",VIOLET);
        FrameLayout.LayoutParams lpR=new FrameLayout.LayoutParams(dp(94),dp(58),Gravity.RIGHT|Gravity.TOP);
        lpR.setMargins(0,dp(58),dp(0),0);
        cockpit.addView(rightHud,lpR);

        orb=new OrbView(c);
        FrameLayout.LayoutParams orbLp=new FrameLayout.LayoutParams(dp(280),dp(280),Gravity.CENTER);
        cockpit.addView(orb,orbLp);

        coreLabel=text("NEXUS CORE // QUANTUM FIELD",8,CYAN,true);
        coreLabel.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams coreLp=new FrameLayout.LayoutParams(dp(220),dp(28),Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM);
        coreLp.setMargins(0,0,0,dp(12));
        cockpit.addView(coreLabel,coreLp);

        TextView dsp=miniPanel("DSP\n64 FLOAT",PINK);
        FrameLayout.LayoutParams dspLp=new FrameLayout.LayoutParams(dp(84),dp(54),Gravity.LEFT|Gravity.BOTTOM);
        dspLp.setMargins(dp(8),0,0,dp(12));
        cockpit.addView(dsp,dspLp);

        TextView path=miniPanel("SIGNAL\nTRANSPARENT",BLUE);
        FrameLayout.LayoutParams pathLp=new FrameLayout.LayoutParams(dp(92),dp(54),Gravity.RIGHT|Gravity.BOTTOM);
        pathLp.setMargins(0,0,dp(8),dp(12));
        cockpit.addView(path,pathLp);

        title=text("Aucun morceau",21,WHITE,true);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(34)));

        subtitle=text("Importe un fichier ou ouvre la Radio",10,CYAN,false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setSingleLine(true);
        root.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(26)));

        LinearLayout diag=row();
        diag.setPadding(dp(12),dp(6),dp(12),dp(6));
        diag.setBackground(glass(CYAN,0x14122A3E,16));

        LinearLayout dleft=column();
        signal=text("Format en attente",9,CYAN,true);
        status=text("AUDIO CORE READY",8,WHITE,false);
        dleft.addView(signal,new LinearLayout.LayoutParams(-1,dp(22)));
        dleft.addView(status,new LinearLayout.LayoutParams(-1,dp(20)));
        diag.addView(dleft,new LinearLayout.LayoutParams(0,dp(44),1f));

        route=text("Android Audio",8,MUTED,false);
        route.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        diag.addView(route,new LinearLayout.LayoutParams(0,dp(44),1f));
        root.addView(diag,new LinearLayout.LayoutParams(-1,dp(56)));

        root.addView(gap(8),new LinearLayout.LayoutParams(1,dp(8)));

        LinearLayout transport=column();
        transport.setPadding(dp(10),dp(6),dp(10),dp(8));
        transport.setBackground(glass(VIOLET,0x5510182E,20));

        seek=new SeekBar(c);
        seek.setMax(1000);
        seek.setProgressTintList(ColorStateList.valueOf(CYAN));
        seek.setThumbTintList(ColorStateList.valueOf(VIOLET));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            boolean user;
            public void onProgressChanged(SeekBar s,int p,boolean f){user=f;}
            public void onStartTrackingTouch(SeekBar s){user=true;}
            public void onStopTrackingTouch(SeekBar s){
                if(user&&actions!=null) actions.onSeekTo(s.getProgress()/1000f);
                user=false;
            }
        });
        transport.addView(seek,new LinearLayout.LayoutParams(-1,dp(28)));

        LinearLayout controls=row();
        time=text("0:00   //   LIVE",8,MUTED,false);
        controls.addView(time,new LinearLayout.LayoutParams(0,dp(44),1f));

        play=text("▶",20,WHITE,true);
        play.setGravity(Gravity.CENTER);
        play.setBackground(glass(CYAN,0x24133446,24));
        play.setOnClickListener(v->{if(actions!=null)actions.onTogglePlayPause();});
        controls.addView(play,new LinearLayout.LayoutParams(dp(72),dp(44)));

        TextView media=text("MEDIA3\nENGINE",7,CYAN,true);
        media.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        controls.addView(media,new LinearLayout.LayoutParams(0,dp(44),1f));
        transport.addView(controls);
        root.addView(transport,new LinearLayout.LayoutParams(-1,dp(82)));

        root.addView(gap(10),new LinearLayout.LayoutParams(1,dp(10)));

        sectionInfo=text("PLAYER // LOCAL HIGH FIDELITY",9,CYAN,true);
        sectionInfo.setGravity(Gravity.CENTER);
        root.addView(sectionInfo,new LinearLayout.LayoutParams(-1,dp(28)));

        actionArea=column();
        primary=action("＋  IMPORTER UN FICHIER AUDIO",CYAN);
        secondary=action("◎  EXPLORER LES RADIOS DU MONDE",PINK);
        third=action("＋  AJOUTER UNE URL DIRECTE",VIOLET);
        primary.setOnClickListener(v->{
            if(actions==null)return;
            if(screen==Screen.HOME)actions.onPickLocalAudio();
            else if(screen==Screen.RADIO)actions.onBrowseRadios();
        });
        secondary.setOnClickListener(v->{if(actions!=null&&screen==Screen.RADIO)actions.onAddRadio();});
        third.setOnClickListener(v->{if(actions!=null&&screen==Screen.RADIO)actions.onResumeLastRadio();});
        actionArea.addView(primary,new LinearLayout.LayoutParams(-1,dp(52)));
        actionArea.addView(gap(6),new LinearLayout.LayoutParams(1,dp(6)));
        actionArea.addView(secondary,new LinearLayout.LayoutParams(-1,dp(52)));
        actionArea.addView(gap(6),new LinearLayout.LayoutParams(1,dp(6)));
        actionArea.addView(third,new LinearLayout.LayoutParams(-1,dp(52)));
        root.addView(actionArea);

        labArea=column();
        labArea.addView(labRow("PURE AUDIO","READY // MEDIA3 DIRECT PATH",CYAN));
        labArea.addView(labRow("EQ PARAMÉTRIQUE 20 BANDES","ROADMAP // DSP NATIF",VIOLET));
        labArea.addView(labRow("CONVOLVER FIR","ROADMAP // HEADPHONE CORRECTION",PINK));
        labArea.addView(labRow("USB DAC / HI-RES","ROADMAP // HARDWARE DETECT",BLUE));
        labArea.addView(labRow("DSD / BIT-PERFECT","ROADMAP // WHEN SUPPORTED",CYAN));
        root.addView(labArea);

        LinearLayout modules=row();
        modules.addView(module("EQ","20 BAND",VIOLET),new LinearLayout.LayoutParams(0,dp(60),1f));
        modules.addView(gap(6),new LinearLayout.LayoutParams(dp(6),1));
        modules.addView(module("SPACE","3D FIELD",CYAN),new LinearLayout.LayoutParams(0,dp(60),1f));
        modules.addView(gap(6),new LinearLayout.LayoutParams(dp(6),1));
        modules.addView(module("RADIO","WORLD",PINK),new LinearLayout.LayoutParams(0,dp(60),1f));
        root.addView(modules,new LinearLayout.LayoutParams(-1,dp(66)));

        TextView privacy=text("PRIVACY CORE // AUCUN PRÉNOM AFFICHÉ // NO ADS",7,MUTED,true);
        privacy.setGravity(Gravity.CENTER);
        root.addView(privacy,new LinearLayout.LayoutParams(-1,dp(34)));

        renderScreen();
    }

    public void setActions(Actions a){actions=a;renderScreen();}
    public void setPlaying(boolean v){playing=v;play.setText(v?"Ⅱ":"▶");orb.setActive(v);hud.setActive(v);}
    public void setTrackTitle(String v){if(v!=null&&!v.isEmpty())title.setText(v);}
    public void setTrackSubtitle(String v){subtitle.setText(v==null?"":v);}
    public void setStatus(String v){status.setText(v==null?"":v);}
    public void setSignalDiagnostics(String s,String out){signal.setText(s==null?"Format en attente":s);route.setText(out==null?"Android Audio":out);}
    public void setProgress(float p,long pos,long dur){
        int next=Math.round(Math.max(0f,Math.min(1f,p))*1000f);
        String label=format(pos)+"   //   "+(dur>0?format(dur):"LIVE");
        if(positionMs==pos&&durationMs==dur&&seek.getProgress()==next&&label.contentEquals(time.getText()))return;
        positionMs=pos;durationMs=dur;
        if(seek.getProgress()!=next)seek.setProgress(next);
        if(!label.contentEquals(time.getText()))time.setText(label);
    }
    public void showHome(){screen=Screen.HOME;renderScreen();}

    private void renderScreen(){
        if(screen==Screen.HOME){
            coreLabel.setText("NEXUS CORE // QUANTUM PLAYER");
            sectionInfo.setText("PLAYER // LOCAL HIGH FIDELITY");
            primary.setText("＋  IMPORTER UN FICHIER AUDIO");
            primary.setVisibility(VISIBLE);
            secondary.setVisibility(GONE);
            third.setVisibility(GONE);
            actionArea.setVisibility(VISIBLE);
            labArea.setVisibility(GONE);
            orb.setVisibility(VISIBLE);
        }else if(screen==Screen.RADIO){
            coreLabel.setText("NEXUS CORE // RADIO FIELD");
            sectionInfo.setText("RADIO // LIVE WORLD SIGNAL");
            primary.setText("◎  EXPLORER LES RADIOS DU MONDE");
            secondary.setText("＋  AJOUTER UNE URL DIRECTE");
            third.setText(actions!=null&&actions.hasSavedRadio()?"↻  REPRENDRE LA DERNIÈRE RADIO":"AUCUNE RADIO ENREGISTRÉE");
            primary.setVisibility(VISIBLE);
            secondary.setVisibility(VISIBLE);
            third.setVisibility(VISIBLE);
            actionArea.setVisibility(VISIBLE);
            labArea.setVisibility(GONE);
            orb.setVisibility(VISIBLE);
        }else{
            coreLabel.setText("NEXUS CORE // AUDIO LAB");
            sectionInfo.setText("AUDIO LAB // SIGNAL ARCHITECTURE");
            actionArea.setVisibility(GONE);
            labArea.setVisibility(VISIBLE);
            orb.setVisibility(VISIBLE);
        }
    }

    private TextView tab(String label,Screen target){
        TextView v=action(label,CYAN);
        v.setTextSize(8);
        v.setOnClickListener(x->{screen=target;renderScreen();});
        return v;
    }

    private TextView action(String label,int accent){
        TextView v=text(label,10,WHITE,true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(glass(accent,0x23101A31,16));
        v.setPadding(dp(8),0,dp(8),0);
        return v;
    }

    private TextView miniPanel(String s,int accent){
        TextView v=text(s,7,accent,true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(glass(accent,0x26101A31,12));
        return v;
    }

    private View labRow(String name,String sub,int accent){
        LinearLayout r=column();
        r.setPadding(dp(12),dp(7),dp(12),dp(5));
        r.setBackground(glass(accent,0x18101A31,14));
        r.addView(text(name,9,WHITE,true),new LinearLayout.LayoutParams(-1,dp(22)));
        r.addView(text(sub,7,accent,false),new LinearLayout.LayoutParams(-1,dp(18)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));
        lp.setMargins(0,0,0,dp(6));
        r.setLayoutParams(lp);
        return r;
    }

    private TextView module(String a,String b,int accent){
        TextView v=text(a+"\n"+b,8,WHITE,true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(glass(accent,0x18101A31,14));
        return v;
    }

    private TextView text(String s,float size,int color,boolean bold){
        TextView v=new TextView(getContext());
        v.setText(s);v.setTextSize(size);v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        return v;
    }

    private LinearLayout row(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(LinearLayout.VERTICAL);return l;}
    private Space gap(int n){Space s=new Space(getContext());s.setMinimumWidth(dp(n));s.setMinimumHeight(dp(n));return s;}

    private GradientDrawable glass(int accent,int fill,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{fill,Color.argb(Color.alpha(fill),4,14,30)});
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1),Color.argb(105,Color.red(accent),Color.green(accent),Color.blue(accent)));
        return d;
    }

    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static String format(long ms){if(ms<=0)return"0:00";long s=ms/1000;return String.format(Locale.US,"%d:%02d",s/60,s%60);}
}
