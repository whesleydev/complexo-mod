package com.lunar.mod;

import android.content.Context;

/**
 * ponte entre o menu e o motor. cada toggle cai aqui.
 * o motor (hooks nativos) e ligado depois; aqui so registra.
 */
public final class Features {

    private static Context appContext;

    private Features() {
    }

    public static void attach(Context context) {
        appContext = context;
    }

    public static Context context() {
        return appContext;
    }

    public static void onToggle(String key, boolean value) {
        Config.append("feature " + key + "=" + value);
        if ("esp".equals(key)) {
            Engine.setEsp(value);
        } else if ("fastattack".equals(key)) {
            Engine.setFastAttack(value);
        }
    }

    public static void onNumber(String key, double value) {
        if ("speed".equals(key)) {
            Engine.setSpeed(value);
        }
    }
}
