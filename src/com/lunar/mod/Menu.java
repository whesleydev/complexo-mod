package com.lunar.mod;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * menu flutuante: bolha arrastavel + painel com abas.
 * roda dentro do processo do jogo (dex injetado), desenha via WindowManager.
 */
public final class Menu {

    private static final int BG = 0xF2121519;
    private static final int CARD = 0xFF1B2028;
    private static final int ACCENT = 0xFFFFAA3C;
    private static final int TEXT = 0xFFE8ECF3;
    private static final int DIM = 0xFF8A94A6;

    private static WindowManager windowManager;
    private static EspView espView;
    private static Loop loop;
    private static FrameLayout bubble;
    private static LinearLayout panel;
    private static boolean attached;

    private Menu() {
    }

    public static void install(final Context context) {
        if (attached) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    build(context);
                    attached = true;
                    Config.append("menu instalado");
                } catch (Throwable error) {
                    Config.append("menu falhou: " + error);
                }
            }
        });
    }

    private static WindowManager.LayoutParams params(int width, int height, int x, int y) {
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(width, height, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = x;
        lp.y = y;
        return lp;
    }

    private static void build(Context context) {
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        espView = new EspView(context);
        WindowManager.LayoutParams espParams = params(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT, 0, 0);
        espParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.addView(espView, espParams);

        loop = new Loop(espView);
        loop.start();
        Config.append("loop de render iniciado");

        bubble = new FrameLayout(context);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFF0E1117);
        circle.setStroke(4, ACCENT);
        bubble.setBackground(circle);

        TextView mark = new TextView(context);
        mark.setText("H");
        mark.setTextColor(ACCENT);
        mark.setTextSize(22);
        mark.setGravity(Gravity.CENTER);
        bubble.addView(mark, new FrameLayout.LayoutParams(-1, -1));

        final WindowManager.LayoutParams bubbleParams = params(140, 140, 40, 500);
        windowManager.addView(bubble, bubbleParams);

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable box = new GradientDrawable();
        box.setColor(BG);
        box.setCornerRadius(28f);
        box.setStroke(3, 0x66FFAA3C);
        panel.setBackground(box);
        panel.setPadding(28, 28, 28, 28);
        panel.setVisibility(View.GONE);

        final WindowManager.LayoutParams panelParams = params(620, 900, 40, 700);
        windowManager.addView(panel, panelParams);

        TextView title = new TextView(context);
        title.setText("LUNAR MOD");
        title.setTextColor(ACCENT);
        title.setTextSize(18);
        title.setPadding(0, 0, 0, 18);
        panel.addView(title);

        ScrollView scroll = new ScrollView(context);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        addSwitch(context, body, "ESP jogadores", "esp");
        addSwitch(context, body, "Aimbot", "aim");
        addSlider(context, body, "FOV mira", "aim_fov", 60.0, 900.0);
        addSlider(context, body, "Escala ESP", "esp_scale", 0.4, 2.5);
        addSwitch(context, body, "Gerar log", "log");

        final TextView log = new TextView(context);
        log.setTextColor(DIM);
        log.setTextSize(10);
        log.setPadding(0, 18, 0, 0);
        log.setText(Config.logText());
        panel.addView(log, new LinearLayout.LayoutParams(-1, 240));

        Button refresh = new Button(context);
        refresh.setText("atualizar log");
        refresh.setTextColor(TEXT);
        refresh.setBackgroundColor(CARD);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                log.setText(Config.logText());
            }
        });
        panel.addView(refresh, new LinearLayout.LayoutParams(-1, 90));

        bubble.setOnTouchListener(new DragTouch(bubbleParams, bubble));
        panel.setOnTouchListener(new DragTouch(panelParams, panel));

        bubble.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
    }

    private static void addSwitch(Context context, LinearLayout parent, String label, final String key) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 10, 0, 10);

        TextView text = new TextView(context);
        text.setText(label);
        text.setTextColor(TEXT);
        text.setTextSize(14);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));

        Switch toggle = new Switch(context);
        toggle.setChecked(Config.flag(key));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Config.set(key, checked);
                Features.onToggle(key, checked);
            }
        });
        row.addView(toggle);
        parent.addView(row);
    }

    private static void addSlider(Context context, LinearLayout parent, String label,
                                  final String key, final double min, final double max) {
        TextView text = new TextView(context);
        text.setText(label + ": " + Config.number(key, 2.0));
        text.setTextColor(TEXT);
        text.setTextSize(14);
        text.setPadding(0, 10, 0, 0);
        parent.addView(text);

        final TextView value = text;
        SeekBar bar = new SeekBar(context);
        bar.setMax(100);
        bar.setProgress((int) ((Config.number(key, 2.0) - min) / (max - min) * 100));
        final String keyRef = key;
        final double minRef = min;
        final double maxRef = max;
        final String labelRef = label;
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double value2 = minRef + (maxRef - minRef) * progress / 100.0;
                value2 = Math.round(value2 * 10) / 10.0;
                value.setText(labelRef + ": " + value2);
                if (fromUser) {
                    Config.set(keyRef, value2);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        parent.addView(bar);
    }

    private static final class DragTouch implements View.OnTouchListener {
        private final WindowManager.LayoutParams params;
        private final View target;
        private int startX;
        private int startY;
        private float downX;
        private float downY;

        DragTouch(WindowManager.LayoutParams params, View target) {
            this.params = params;
            this.target = target;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = params.x;
                    startY = params.y;
                    downX = event.getRawX();
                    downY = event.getRawY();
                    return false;
                case MotionEvent.ACTION_MOVE:
                    params.x = startX + (int) (event.getRawX() - downX);
                    params.y = startY + (int) (event.getRawY() - downY);
                    try {
                        windowManager.updateViewLayout(target, params);
                    } catch (Throwable ignored) {
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
}
