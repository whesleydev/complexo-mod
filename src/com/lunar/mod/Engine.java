package com.lunar.mod;

/**
 * motor nativo. quando liblunar.so estiver presente, os metodos sao
 * substituidos por chamadas JNI. sem a lib, tudo cai em modo registro.
 */
public final class Engine {

    private static boolean loaded;

    static {
        try {
            System.loadLibrary("lunar");
            loaded = true;
            Config.append("engine nativa carregada");
        } catch (Throwable error) {
            loaded = false;
            Config.append("engine nativa ausente (modo registro)");
        }
    }

    private Engine() {
    }

    public static boolean loaded() {
        return loaded;
    }

    public static void setEsp(boolean value) {
        Config.append("engine esp=" + value + (loaded ? " (nativo)" : " (registro)"));
        if (loaded) {
            try {
                nativeSetEsp(value);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void setFastAttack(boolean value) {
        Config.append("engine fastattack=" + value + (loaded ? " (nativo)" : " (registro)"));
        if (loaded) {
            try {
                nativeSetFastAttack(value);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void setSpeed(double value) {
        Config.append("engine speed=" + value + (loaded ? " (nativo)" : " (registro)"));
        if (loaded) {
            try {
                nativeSetSpeed(value);
            } catch (Throwable ignored) {
            }
        }
    }

    private static native void nativeSetEsp(boolean value);

    private static native void nativeSetFastAttack(boolean value);

    private static native void nativeSetSpeed(double value);
}
