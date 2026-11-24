package com.lunar.mod;

/**
 * ponte JNI para liblunar.so. roda na thread principal do Android,
 * que em jogo Unity e a mesma thread do motor.
 */
public final class Engine {

    private static boolean loaded;
    private static boolean ready;

    static {
        try {
            System.loadLibrary("lunar");
            loaded = true;
            Config.append("liblunar carregada");
        } catch (Throwable error) {
            loaded = false;
            Config.append("liblunar ausente: " + error);
        }
    }

    private Engine() {
    }

    public static boolean loaded() {
        return loaded;
    }

    public static boolean ready() {
        if (!loaded) {
            return false;
        }
        if (!ready) {
            try {
                ready = nativeInit();
                Config.append("alvos il2cpp: " + (ready ? "resolvidos" : "nao resolvidos"));
            } catch (Throwable error) {
                Config.append("init falhou: " + error);
                return false;
            }
        }
        return ready;
    }

    public static float[] tick(boolean esp, boolean aim, float fov) {
        if (!ready()) {
            return null;
        }
        try {
            return nativeTick(esp, aim, fov);
        } catch (Throwable error) {
            return null;
        }
    }

    public static String status() {
        try {
            return loaded ? nativeStatus() : "lib ausente";
        } catch (Throwable error) {
            return "erro: " + error;
        }
    }

    public static void setEsp(boolean value) {
        Config.append("esp=" + value);
    }

    public static void setFastAttack(boolean value) {
        Config.append("fastattack=" + value);
    }

    public static void setSpeed(double value) {
        Config.append("speed=" + value);
    }

    private static native boolean nativeInit();

    private static native float[] nativeTick(boolean esp, boolean aim, float fov);

    private static native String nativeStatus();
}
