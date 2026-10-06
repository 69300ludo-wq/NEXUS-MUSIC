package com.nexusmusic.player;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

public final class NexusHudBackdrop extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long start = SystemClock.uptimeMillis();
    private boolean active;

    public NexusHudBackdrop(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(1));
    }

    public void setActive(boolean v) {
        active = v;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w=getWidth(), h=getHeight(), t=(SystemClock.uptimeMillis()-start)/1000f;

        p.setShader(new LinearGradient(0,0,w,h,
                new int[]{0xFF01030A,0xFF061026,0xFF120722,0xFF02040B},
                null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p); p.setShader(null);

        p.setShader(new RadialGradient(w*.5f,h*.30f,w*.58f,
                new int[]{0x332F8CFF,0x1A6B3CFF,0x00000000},
                null,Shader.TileMode.CLAMP));
        c.drawCircle(w*.5f,h*.30f,w*.58f,p); p.setShader(null);

        for(int i=0;i<90;i++){
            float x=((i*67)%101)/100f*w;
            float y=((i*43)%103)/102f*h;
            int a=35+(int)(50*Math.abs(Math.sin(t*.35+i)));
            p.setColor(Color.argb(a,180,235,255));
            c.drawCircle(x,y,dp(i%17==0?1.2f:.5f),p);
        }

        line.setColor(0x223DF7FF);
        for(int i=0;i<8;i++){
            float y=dp(118)+i*dp(82);
            c.drawLine(dp(8),y,w-dp(8),y,line);
        }
        for(int i=0;i<6;i++){
            float x=dp(14)+i*(w-dp(28))/5f;
            c.drawLine(x,dp(105),x,h-dp(120),line);
        }

        drawCorner(c,dp(10),dp(110),1,1);
        drawCorner(c,w-dp(10),dp(110),-1,1);
        drawCorner(c,dp(10),h-dp(130),1,-1);
        drawCorner(c,w-dp(10),h-dp(130),-1,-1);

        float cx=w*.5f, cy=dp(300);
        line.setStrokeWidth(dp(1.2f));
        for(int i=0;i<3;i++){
            float rr=dp(120+i*26);
            line.setColor(i%2==0?0x3348F7FF:0x337D55FF);
            RectF r=new RectF(cx-rr,cy-rr*.42f,cx+rr,cy+rr*.42f);
            c.save();
            c.rotate(t*(4+i*2)+i*24,cx,cy);
            c.drawOval(r,line);
            c.restore();
        }

        line.setColor(0x446A7CFF);
        c.drawCircle(cx,cy,dp(155),line);
        c.drawCircle(cx,cy,dp(180),line);

        p.setColor(0x223DF7FF);
        for(int i=0;i<18;i++){
            double a=i*Math.PI*2/18.0+t*.18;
            float x=(float)(cx+Math.cos(a)*dp(180));
            float y=(float)(cy+Math.sin(a)*dp(180));
            c.drawCircle(x,y,dp(i%3==0?2.2f:1.1f),p);
        }

        drawWave(c,w,h,t);

        if(active) postInvalidateDelayed(33);
    }

    private void drawWave(Canvas c,float w,float h,float t){
        float base=h-dp(165), left=dp(28), right=w-dp(28);
        int n=38;
        for(int i=0;i<n;i++){
            float x=left+(right-left)*i/(n-1f);
            float amp=(float)(4+12*Math.abs(Math.sin(i*.63+t*2.4)));
            p.setColor(i%3==0?0x886C4DFF:0x8852F0FF);
            c.drawRoundRect(new RectF(x,base-amp,x+dp(2),base+amp),
                    dp(1),dp(1),p);
        }
    }

    private void drawCorner(Canvas c,float x,float y,int sx,int sy){
        line.setColor(0x775FF5FF);
        float a=dp(28),b=dp(10);
        c.drawLine(x,y,x+sx*a,y,line);
        c.drawLine(x,y,x,y+sy*a,line);
        c.drawLine(x+sx*a,y,x+sx*(a+b),y+sy*b,line);
        c.drawLine(x,y+sy*a,x+sx*b,y+sy*(a+b),line);
    }

    private float dp(float v){
        return v*getResources().getDisplayMetrics().density;
    }
}
