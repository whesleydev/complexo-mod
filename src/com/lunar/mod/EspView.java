package com.lunar.mod;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/**
 * camada de desenho. transparente, nao recebe toque,
 * cobre a tela inteira e desenha por cima do jogo.
 */
public final class EspView extends View {

    private final Paint box = new Paint();
    private final Paint text = new Paint();
    private final Paint health = new Paint();
    private final Paint bar = new Paint();

    private float[] data = null;
    private float scale = 1.0f;

    public EspView(Context context) {
        super(context);
        box.setStyle(Paint.Style.STROKE);
        box.setStrokeWidth(3.0f);
        box.setColor(Color.rgb(255, 170, 60));
        box.setAntiAlias(true);

        text.setColor(Color.WHITE);
        text.setTextSize(26.0f);
        text.setAntiAlias(true);
        text.setShadowLayer(4.0f, 0.0f, 0.0f, Color.BLACK);

        health.setColor(Color.rgb(80, 220, 120));
        bar.setColor(Color.argb(160, 0, 0, 0));
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
    }

    public void setData(float[] values) {
        data = values;
        invalidate();
    }

    public void setScale(float value) {
        scale = value;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null || data.length < 5) {
            return;
        }
        int height = getHeight();
        int count = (int) data[0];
        for (int index = 0; index < count; index++) {
            int base = 1 + index * 5;
            if (base + 4 >= data.length) {
                break;
            }
            float x = data[base];
            float y = height - data[base + 1];
            float distance = data[base + 2];
            float hp = data[base + 3];
            float width = data[base + 4] * scale;
            if (width <= 1.0f) {
                width = 60.0f;
            }
            float half = width * 0.5f;
            float boxHeight = width * 2.0f;

            canvas.drawRect(x - half, y - boxHeight, x + half, y, box);

            float healthWidth = width * (hp / 100.0f);
            canvas.drawRect(x - half, y + 6.0f, x + half, y + 14.0f, bar);
            canvas.drawRect(x - half, y + 6.0f, x - half + healthWidth, y + 14.0f, health);

            canvas.drawText(String.format("%.0fm", distance), x - half, y - boxHeight - 8.0f, text);
            canvas.drawText(String.format("%.0f", hp), x - half, y + 34.0f, text);
        }
    }
}
