package com.foonyresso.ouravex;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Самооновлення: версія й APK беруться з публічного релізу "latest" на GitHub. */
public final class Updater {
    public static final int IDLE = 0, AVAILABLE = 1, DOWNLOADING = 2, INSTALLING = 3, ERROR = 4;
    private static final String BASE = "https://github.com/foonyresso/ouravex/releases/download/latest/";
    private static final String ACTION = "com.foonyresso.ouravex.INSTALL_RESULT";

    public static volatile int state = IDLE;
    public static volatile float progress;
    public static volatile String name = "", msg = "";
    private static boolean checked, regd;

    private Updater() {}

    @SuppressWarnings("deprecation")
    private static int localCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) { return 0; }
    }

    public static void check(final Context ctx) {
        if (checked) return;
        checked = true;
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection h = open(BASE + "version.json");
                    InputStream in = h.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] b = new byte[4096];
                    int n;
                    while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                    in.close();
                    JSONObject j = new JSONObject(bo.toString("UTF-8"));
                    if (j.getInt("code") > localCode(c)) {
                        name = j.optString("name", "");
                        state = AVAILABLE;
                    }
                } catch (Exception e) {
                    checked = false; // спробуємо знову при наступному вході
                }
            }
        }).start();
    }

    private static HttpURLConnection open(String u) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        h.setConnectTimeout(8000);
        h.setReadTimeout(15000);
        h.setInstanceFollowRedirects(true);
        return h;
    }

    public static void start(final Context ctx) {
        final Context c = ctx.getApplicationContext();
        if (state != AVAILABLE && state != ERROR) return;
        if (Build.VERSION.SDK_INT >= 26 && !c.getPackageManager().canRequestPackageInstalls()) {
            msg = "Дозволь встановлення з цієї гри й торкнись знову";
            state = ERROR;
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return;
        }
        state = DOWNLOADING;
        progress = 0f;
        new Thread(new Runnable() {
            public void run() {
                try {
                    File f = new File(c.getCacheDir(), "update.apk");
                    HttpURLConnection h = open(BASE + "ouravex-a.apk");
                    long total = h.getContentLength();
                    InputStream in = h.getInputStream();
                    OutputStream o = new FileOutputStream(f);
                    byte[] b = new byte[16384];
                    long got = 0;
                    int n;
                    while ((n = in.read(b)) > 0) {
                        o.write(b, 0, n);
                        got += n;
                        if (total > 0) progress = got / (float) total;
                    }
                    o.close(); in.close();
                    install(c, f);
                } catch (Exception e) {
                    msg = "Помилка завантаження";
                    state = ERROR;
                }
            }
        }).start();
    }

    private static void install(Context c, File f) throws Exception {
        if (!regd) {
            regd = true;
            final Context app = c;
            BroadcastReceiver r = new BroadcastReceiver() {
                public void onReceive(Context ctx, Intent it) {
                    int st = it.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
                    if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        Intent conf = it.getParcelableExtra(Intent.EXTRA_INTENT);
                        if (conf != null) {
                            conf.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            app.startActivity(conf);
                        }
                    } else if (st != PackageInstaller.STATUS_SUCCESS) {
                        msg = "Не вдалося встановити (" + st + ")";
                        state = ERROR;
                    }
                }
            };
            if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(r, new IntentFilter(ACTION), Context.RECEIVER_NOT_EXPORTED);
            else c.registerReceiver(r, new IntentFilter(ACTION));
        }
        state = INSTALLING;
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (Build.VERSION.SDK_INT >= 31) sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = pi.createSession(sp);
        PackageInstaller.Session s = pi.openSession(id);
        OutputStream o = s.openWrite("update", 0, f.length());
        InputStream in = new java.io.FileInputStream(f);
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        s.fsync(o);
        in.close(); o.close();
        Intent it = new Intent(ACTION).setPackage(c.getPackageName());
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        s.commit(PendingIntent.getBroadcast(c, id, it, fl).getIntentSender());
        s.close();
    }
}
