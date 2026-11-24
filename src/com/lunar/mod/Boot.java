package com.lunar.mod;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

/**
 * ponto de entrada. ContentProvider roda antes da Activity, sem
 * precisar tocar no codigo original do jogo.
 */
public final class Boot extends ContentProvider {

    @Override
    public boolean onCreate() {
        final Context context = getContext();
        if (context == null) {
            return true;
        }
        try {
            Config.load(context);
            Features.attach(context);
            Config.append("boot no processo do jogo");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Menu.install(context);
                }
            }, 4000);
        } catch (Throwable error) {
            android.util.Log.e("LunarMod", "boot: " + error);
        }
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
