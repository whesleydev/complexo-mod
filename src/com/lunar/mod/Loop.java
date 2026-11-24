package com.lunar.mod;

import android.view.Choreographer;

/**
 * um tick por frame, na thread principal. e essa thread que
 * tem permissao para chamar as APIs do Unity.
 */
public final class Loop implements Choreographer.FrameCallback {

    private final EspView view;
    private boolean running;
    private long frames;
    private long lastLog;

    public Loop(EspView view) {
        this.view = view;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void stop() {
        running = false;
    }

    @Override
    public void doFrame(long nanos) {
        if (!running) {
            return;
        }
        try {
            boolean esp = Config.flag("esp");
            boolean aim = Config.flag("aim");
            float fov = (float) Config.number("aim_fov", 260.0);
            view.setScale((float) Config.number("esp_scale", 1.0));
            float[] data = esp || aim ? Engine.tick(esp, aim, fov) : null;
            if (esp) {
                view.setData(data);
            }
            frames++;
            if (System.currentTimeMillis() - lastLog > 15000) {
                lastLog = System.currentTimeMillis();
                Config.append("tick " + frames + " frames | " + Engine.status());
                frames = 0;
            }
        } catch (Throwable error) {
            Config.append("loop erro: " + error);
        }
        Choreographer.getInstance().postFrameCallback(this);
    }
}
