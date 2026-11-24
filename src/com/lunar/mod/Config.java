package com.lunar.mod;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * config em texto puro no diretorio externo do proprio app.
 * formato: chave=valor, uma por linha. sem json, sem dependencia.
 */
public final class Config {

    private static final Map<String, String> values = new LinkedHashMap<String, String>();
    private static File file;
    private static final List<String> logLines = new ArrayList<String>();

    private Config() {
    }

    public static synchronized void load(Context context) {
        if (file == null) {
            File dir = null;
            try {
                dir = context.getExternalFilesDir(null);
            } catch (Throwable ignored) {
            }
            if (dir == null) {
                dir = context.getFilesDir();
            }
            file = new File(dir, "mod.cfg");
        }
        if (!file.exists()) {
            put("speed", "2.0");
            put("fastattack", "false");
            put("esp", "false");
            put("esp_name", "true");
            put("esp_distance", "true");
            put("log", "true");
            save();
            return;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                sb.append(new String(buffer, 0, read, "UTF-8"));
            }
            for (String line : sb.toString().split("\n")) {
                String trimmed = line.trim();
                if (trimmed.length() == 0 || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq > 0) {
                    values.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
                }
            }
        } catch (Throwable ignored) {
        } finally {
            close(in);
        }
    }

    public static synchronized void save() {
        if (file == null) {
            return;
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            for (Map.Entry<String, String> entry : values.entrySet()) {
                out.write((entry.getKey() + "=" + entry.getValue() + "\n").getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        } finally {
            close(out);
        }
    }

    public static synchronized void put(String key, String value) {
        values.put(key, value);
    }

    public static synchronized String get(String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value;
    }

    public static boolean flag(String key) {
        return "true".equalsIgnoreCase(get(key, "false"));
    }

    public static double number(String key, double fallback) {
        try {
            return Double.parseDouble(get(key, String.valueOf(fallback)));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static synchronized void set(String key, boolean value) {
        values.put(key, value ? "true" : "false");
        save();
        append("cfg " + key + "=" + value);
    }

    public static synchronized void set(String key, double value) {
        values.put(key, String.valueOf(value));
        save();
        append("cfg " + key + "=" + value);
    }

    public static synchronized void append(String line) {
        String stamp = String.format("[%02d:%02d:%02d] ", (System.currentTimeMillis() / 3600000) % 24,
                (System.currentTimeMillis() / 60000) % 60, (System.currentTimeMillis() / 1000) % 60);
        logLines.add(stamp + line);
        while (logLines.size() > 200) {
            logLines.remove(0);
        }
        if (flag("log")) {
            android.util.Log.i("LunarMod", line);
        }
    }

    public static synchronized String logText() {
        StringBuilder sb = new StringBuilder();
        for (String line : logLines) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static void close(java.io.Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }
}
