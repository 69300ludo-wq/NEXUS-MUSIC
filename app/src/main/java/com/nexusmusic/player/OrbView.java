package com.nexusmusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.View;

public final class OrbView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long start = SystemClock.uptimeMillis();
    private boolean active;

    public OrbView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(1f));
    }

    public void setActive(boolean value) {
        active = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float x = w / 2f, y = h / 2f;
        float r = Math.min(w, h) * .30f;
        float t = (SystemClock.uptimeMillis() - start) / 1000f;

        for (int i = 0; i < 4; i++) {
            float rr = r + dp(i * 10);
            line.setColor(alpha(i % 2 == 0 ? 0xFF5FF5FF : 0xFFAC5AFF, .34f));
            RectF oval = new RectF(x - rr, y - rr * .42f, x + rr, y + rr * .42f);
            c.save();
            c.rotate(t * (9 + i * 4) + i * 27, x, y);
            c.drawOval(oval, line);
            c.restore();
        }

        p.setShader(new RadialGradient(x-r*.24f, y-r*.28f, r,
                new int[]{Color.WHITE, 0xFF46A8FF, 0xFF5C2CD2, 0xFF080816},
                null, Shader.TileMode.CLAMP));
        p.setShadowLayer(dp(28), 0, 0, alpha(0xFFAC5AFF, .70f));
        c.drawCircle(x, y, r * .62f, p);
        p.clearShadowLayer();
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(3));
        p.setShader(new SweepGradient(x, y,
                new int[]{0xFF5FF5FF, Color.TRANSPARENT, 0xFFAC5AFF,
                        Color.TRANSPARENT, 0xFFF44EFF, 0xFF5FF5FF}, null));
        c.save();
        c.rotate(t * 18, x, y);
        c.drawArc(new RectF(x-r*.80f, y-r*.80f, x+r*.80f, y+r*.80f),
                -25, 275, false, p);
        c.restore();
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);

        if (active) postInvalidateDelayed(33L);
    }

    private int alpha(int color, float value) {
        return Color.argb(Math.round(value * 255f),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
