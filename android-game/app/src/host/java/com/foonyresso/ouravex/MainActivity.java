package com.foonyresso.ouravex;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import dalvik.system.DexClassLoader;

/**
 * Хост: лише завантажувач. Уся гра лежить у game.dex, який можна оновлювати
 * без перевстановлення APK (див. Updater у коді гри).
 */
public class MainActivity extends Activity {
    public static final int HOST_API = 1;
    private View gameView;
    private Method mResume, mPause, mBack;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        System.setProperty("ouravex.hostapi", String.valueOf(HOST_API));
        loadGame();
    }

    private int embeddedCode() {
        try {
            InputStream in = getAssets().open("game.json");
            byte[] b = new byte[in.available()];
            in.read(b);
            in.close();
            return new JSONObject(new String(b, "UTF-8")).getInt("code");
        } catch (Exception e) {
            return 0;
        }
    }

    private static void copy(InputStream in, File out) throws Exception {
        OutputStream o = new FileOutputStream(out);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        o.close();
        in.close();
    }

    private static int codeOf(File f) {
        try {
            String n = f.getName();
            return Integer.parseInt(n.substring(5, n.length() - 4));
        } catch (Exception e) {
            return -1;
        }
    }

    /** Повертає файл dex найновішої версії (завантаженої або вбудованої). */
    private File pickDex(boolean allowDownloaded) throws Exception {
        File dir = new File(getFilesDir(), "gamedex");
        dir.mkdirs();
        int emb = embeddedCode();
        File best = null;
        int bestCode = -1;
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                int c = codeOf(f);
                if (c < 0 || !f.getName().endsWith(".dex")) continue;
                if (c > bestCode) { bestCode = c; best = f; }
            }
        }
        if (allowDownloaded && best != null && bestCode >= emb) {
            // прибираємо старіші
            if (fs != null) for (File f : fs) if (f != best && f.getName().endsWith(".dex")) { f.setWritable(true); f.delete(); }
            System.setProperty("ouravex.gamecode", String.valueOf(bestCode));
            return best;
        }
        File e = new File(dir, "game-" + emb + ".dex");
        if (!e.exists()) {
            copy(getAssets().open("game.dex"), e);
            e.setReadOnly();
        }
        System.setProperty("ouravex.gamecode", String.valueOf(emb));
        return e;
    }

    private View instantiate(File dex) throws Exception {
        File opt = getCodeCacheDir();
        DexClassLoader cl = new DexClassLoader(dex.getAbsolutePath(), opt.getAbsolutePath(), null, MainActivity.class.getClassLoader());
        Class<?> k = cl.loadClass("com.foonyresso.ouravex.GameView");
        View v = (View) k.getConstructor(android.content.Context.class).newInstance(this);
        mResume = k.getMethod("onResumeGame");
        mPause = k.getMethod("onPauseGame");
        mBack = k.getMethod("onBack");
        return v;
    }

    private void loadGame() {
        View v = null;
        try {
            v = instantiate(pickDex(true));
        } catch (Throwable t) {
            // зіпсоване оновлення: видаляємо й пробуємо вбудовану версію
            try {
                File[] fs = new File(getFilesDir(), "gamedex").listFiles();
                if (fs != null) for (File f : fs) { f.setWritable(true); f.delete(); }
                v = instantiate(pickDex(false));
            } catch (Throwable t2) {
                TextView tv = new TextView(this);
                tv.setText("Помилка запуску гри: " + t2);
                tv.setTextColor(0xFFFFFFFF);
                v = tv;
            }
        }
        gameView = v;
        setContentView(v);
        hideSystemUi();
        if (resumed) call(mResume);
    }

    /** Викликається грою (через рефлексію) після завантаження оновлення. */
    public void reloadGame() {
        if (resumed) call(mPause);
        loadGame();
    }

    private Object call(Method m) {
        try { return m == null ? null : m.invoke(gameView); } catch (Throwable t) { return null; }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        hideSystemUi();
        call(mResume);
    }

    @Override
    protected void onPause() {
        resumed = false;
        call(mPause);
        super.onPause();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        Object r = call(mBack);
        if (!(r instanceof Boolean) || !((Boolean) r)) super.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
