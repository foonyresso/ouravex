package com.foonyresso.ouravex;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Touch input, screens (title / pause / level-up / death) and HUD. */
public class GameView extends View {
    private static final int TITLE = 0, PLAY = 1, PAUSE = 2, DEAD = 3, INTRO = 4, BEST = 5, SELECT = 6, SETTINGS = 7, SPLASH = 8;
    private int bestTab = 0, bestSel = -1;
    private float introT;
    private static final int B_L = 0, B_R = 1, B_ATK = 2, B_SK = 3, B_INT = 7, B_PAUSE = 8, B_ATK2 = 9, NB = 10;

    private final Audio audio = new Audio();
    private final Game game = new Game(audio);
    private final Render render = new Render();
    private final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private final float[] prevPose = new float[Rig.N];
    private final SharedPreferences prefs;

    private int screen = SPLASH;
    private float splashT, splashBar;
    private boolean splashDone, resetArm;
    private int lastSavedStage = -1;
    private boolean isMenu() { return screen == TITLE || screen == SELECT || screen == SETTINGS || screen == BEST || screen == SPLASH; }
    private int selCls = 0, selDiff = 1, lastCls = 0, lastDiff = 1;
    private int W, H;
    private float S = 1f;
    private long lastNanos;
    private float uiT, lvlT, deadT;
    private int bestStage, bestKills;

    // buttons
    private final float[] bx = new float[NB], by = new float[NB], br = new float[NB];
    private final int[] ptrBtn = new int[20];
    private final boolean[] bDown = new boolean[NB];
    private final boolean[] bSwiped = new boolean[NB];
    private final float[] bDownX = new float[NB], bDownY = new float[NB], bDownT = new float[NB];
    private final float[] bUpT = new float[NB], bDur = new float[NB];
    private float lastJumpEvT = -9f, lastSlideEvT = -9f;
    private boolean crouchLatch;
    private String interactLabel;

    public GameView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("ouravex", Context.MODE_PRIVATE);
        bestStage = prefs.getInt("bestStage", 0);
        bestKills = prefs.getInt("bestKills", 0);
        Codex.init(prefs);
        audio.musicVol = prefs.getInt("musV", 10) / 10f;
        audio.sfxVol = prefs.getInt("sfxV", 10) / 10f;
        Fx.shakeOn = prefs.getBoolean("shake", true);
        setKeepScreenOn(true);
        for (int i = 0; i < ptrBtn.length; i++) ptrBtn[i] = -1;
        for (int i = 0; i < NB; i++) { bUpT[i] = -9f; bDur[i] = 9f; }
        game.newRun(0, 1);
        Rig.copy(prevPose, Rig.IDLE);
    }

    // ---------------- lifecycle ----------------

    public void onResumeGame() {
        audio.start();
        Updater.check(getContext());
        audio.setMusic(isMenu() ? Audio.MUS_TITLE : (game.boss != null ? Audio.MUS_BOSS : Audio.MUS_STAGE));
        lastNanos = 0;
    }

    public void onPauseGame() {
        if (screen == PLAY && !game.levelUpPending) {
            screen = PAUSE;
            audio.setDuck(true);
        }
        if (screen == PLAY || screen == PAUSE) SaveGame.save(prefs, game);
        resetInput();
        audio.setCharging(false, 0f);
        audio.stop();
    }

    public boolean onBack() {
        if (screen == PLAY && !game.levelUpPending) { screen = PAUSE; clearSel(); audio.setDuck(true); resetInput(); return true; }
        if (screen == PAUSE) { if (cfKind != 0) { cfKind = 0; return true; } if (pickerOpen) { pickerOpen = false; return true; } screen = PLAY; audio.setDuck(false); return true; }
        if (screen == DEAD) { toTitle(); return true; }
        if (screen == BEST || screen == SELECT || screen == SETTINGS) { screen = TITLE; return true; }
        return false;
    }

    private void toTitle() {
        lastSavedStage = -1;
        screen = TITLE;
        audio.setDuck(false);
        audio.setMusic(Audio.MUS_TITLE);
        game.newRun(selCls, selDiff);
        resetInput();
    }

    private void startRun() {
        lastSavedStage = -1;
        lastCls = selCls;
        lastDiff = selDiff;
        game.newRun(selCls, selDiff);
        screen = INTRO;
        introT = 0f;
        audio.setDuck(false);
        audio.play(Audio.TRANSFORM, 0.4f);
        resetInput();
    }

    private void beginPlay() {
        screen = PLAY;
        audio.setDuck(false);
        audio.play(Audio.TRANSFORM, 0.5f);
        resetInput();
    }

    private void resetInput() {
        for (int i = 0; i < ptrBtn.length; i++) ptrBtn[i] = -1;
        for (int i = 0; i < NB; i++) { bDown[i] = false; bSwiped[i] = false; }
        crouchLatch = false;
        Game.Ctl c = game.ctl;
        c.left = c.right = c.chargeHeld = c.crouchHeld = c.atkHeld = c.dualAtk = false;
        c.clearEvents();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        W = w; H = h; S = h / 400f;
        // кнопки руху по нижніх кутах, удар над ними, малі уміння навколо
        float lx = 62 * S, rx = W - 62 * S, yy = H - 58 * S;
        bx[B_L] = lx; by[B_L] = yy; br[B_L] = 44 * S;
        bx[B_R] = rx; by[B_R] = yy; br[B_R] = 44 * S;
        bx[B_ATK] = lx + 8 * S; by[B_ATK] = yy - 150 * S; br[B_ATK] = 36 * S;
        bx[B_ATK2] = rx - 8 * S; by[B_ATK2] = yy - 150 * S; br[B_ATK2] = 36 * S;
        float[][] pos = {{lx, 0f}, {lx, -40f}, {rx, 180f}, {rx, 220f}};
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(pos[i][1]);
            bx[B_SK + i] = pos[i][0] + (float) Math.cos(a) * 86 * S;
            by[B_SK + i] = yy + (float) Math.sin(a) * 86 * S;
            br[B_SK + i] = 24 * S;
        }
        bx[B_INT] = W * 0.5f; by[B_INT] = H - 64 * S; br[B_INT] = 44 * S;
        bx[B_PAUSE] = W - 30 * S; by[B_PAUSE] = 28 * S; br[B_PAUSE] = 20 * S;
    }

    // ---------------- input ----------------

    private int hitButton(float x, float y) {
        int best = -1;
        float bd = 1e9f;
        for (int i = 0; i < NB; i++) {
            if (i >= B_SK && i < B_SK + 4 && game.p.slotSkill[i - B_SK] < 0) continue;
            if (i == B_INT && interactLabel == null) continue;
            float dx = x - bx[i], dy = y - by[i];
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float rr = br[i] * (i <= B_R ? 1.25f : 1.15f);
            if (d < rr && d < bd) { bd = d; best = i; }
        }
        return best;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int act = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (act) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                onDown(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) onMove(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                onUp(e.getPointerId(idx));
                break;
            case MotionEvent.ACTION_CANCEL:
                resetInput();
                break;
            default:
                break;
        }
        return true;
    }

    private void onDown(int id, float x, float y) {
        if (screen == PAUSE) { pauseDown(id, x, y); return; }
        if (screen != PLAY) { menuTap(x, y); return; }
        if (game.levelUpPending) { cardTap(x, y); return; }
        int b = hitButton(x, y);
        if (id < ptrBtn.length) ptrBtn[id] = b;
        if (b < 0) return;
        if (crouchLatch) crouchLatch = false;
        bDown[b] = true; bDownX[b] = x; bDownY[b] = y; bDownT[b] = uiT; bSwiped[b] = false;
        Game.Ctl c = game.ctl;
        if (b == B_L || b == B_R) {
            if (uiT - bUpT[b] < 0.26f && bDur[b] < 0.22f) {
                c.dashReq = true;
                c.dashDir = b == B_L ? -1 : 1;
                bUpT[b] = -9f;
            }
        } else if (b == B_ATK || b == B_ATK2) {
            c.atkReq = true;
        } else if (b >= B_SK && b < B_SK + 4) {
            int sk = game.p.slotSkill[b - B_SK];
            if (sk >= 0) c.skillReq[sk] = true;
        } else if (b == B_INT) {
            c.interactReq = true;
        } else if (b == B_PAUSE) {
            screen = PAUSE;
            clearSel();
            SaveGame.save(prefs, game);
            audio.setDuck(true);
            audio.play(Audio.CLICK, 0.6f);
            resetInput();
        }
    }

    private void onMove(int id, float x, float y) {
        if (screen == PAUSE) { pauseMove(id, x, y); return; }
        if (screen != PLAY || id >= ptrBtn.length) return;
        int b = ptrBtn[id];
        if (b < 0 || b > B_R || bSwiped[b]) return;
        float dy = y - bDownY[b];
        float th = 34 * S;
        Game.Ctl c = game.ctl;
        int dir = b == B_L ? -1 : 1;
        if (dy < -th) {
            bSwiped[b] = true;
            if (uiT - lastJumpEvT < 0.2f) {
                if (c.jumpReq) c.jumpDir = 0; else game.p.convertJumpVertical();
            } else {
                c.jumpReq = true; c.jumpDir = dir; lastJumpEvT = uiT;
            }
        } else if (dy > th) {
            bSwiped[b] = true;
            if (uiT - lastSlideEvT < 0.2f) {
                crouchLatch = true;
                c.slideReq = false;
            } else {
                c.slideReq = true; c.slideDir = dir; lastSlideEvT = uiT;
            }
        }
    }

    private void onUp(int id) {
        if (screen == PAUSE) { pauseUp(id); return; }
        if (screen != PLAY || id >= ptrBtn.length) return;
        int b = ptrBtn[id];
        ptrBtn[id] = -1;
        if (b < 0) return;
        bDown[b] = false;
        if (b <= B_R) { bUpT[b] = uiT; bDur[b] = uiT - bDownT[b]; }
    }

    // ---------------- menus ----------------

    private boolean in(float x, float y, float cx, float cy, float w, float h) {
        return x > cx - w / 2 && x < cx + w / 2 && y > cy - h / 2 && y < cy + h / 2;
    }

    private void menuTap(float x, float y) {
        float s = S;
        if (screen == BEST) { bestTap(x, y); return; }
        if (screen == SPLASH) {
            boolean apk = Updater.state == Updater.AVAILABLE && Updater.isApk();
            if (apk && in(x, y, W / 2f, 300 * s, 260 * s, 34 * s)) { Updater.start(getContext()); return; }
            if (splashBar > 0.99f && Updater.state != Updater.DOWNLOADING && Updater.state != Updater.INSTALLING
                    && in(x, y, W / 2f, 350 * s, 220 * s, 40 * s)) { screen = TITLE; audio.play(Audio.CLICK, 0.7f); }
        } else if (screen == TITLE) {
            boolean has = SaveGame.exists(prefs);
            for (int i = 0; i < 4; i++) {
                if (!in(x, y, 150 * s, (150 + i * 46) * s, 236 * s, 38 * s)) continue;
                audio.play(Audio.CLICK, 0.7f);
                if (i == 0) {
                    if (has && SaveGame.load(prefs, game)) {
                        lastCls = game.p.cls; lastDiff = game.diff; selCls = lastCls; selDiff = lastDiff;
                        lastSavedStage = game.stage;
                        beginPlay();
                    }
                } else if (i == 1) { screen = SELECT; game.newRun(selCls, selDiff); }
                else if (i == 2) { screen = BEST; bestSel = -1; }
                else { screen = SETTINGS; resetArm = false; }
            }
        } else if (screen == SELECT) {
            if (in(x, y, 70 * s, 26 * s, 110 * s, 30 * s)) { screen = TITLE; audio.play(Audio.CLICK, 0.6f); return; }
            for (int i = 0; i < 2; i++) {
                if (in(x, y, W / 2f + (i == 0 ? -118 : 118) * s, 160 * s, 220 * s, 150 * s)) {
                    selCls = i; audio.play(Audio.CLICK, 0.7f);
                    game.newRun(selCls, selDiff);
                }
            }
            for (int i = 0; i < 3; i++) {
                if (in(x, y, W / 2f + (i - 1) * 128 * s, 268 * s, 116 * s, 34 * s)) {
                    selDiff = i; audio.play(Audio.CLICK, 0.7f);
                }
            }
            if (in(x, y, W / 2f, 332 * s, 240 * s, 48 * s)) startRun();
        } else if (screen == SETTINGS) {
            if (in(x, y, 70 * s, 26 * s, 110 * s, 30 * s)) { screen = TITLE; audio.play(Audio.CLICK, 0.6f); return; }
            float cx = W / 2f;
            for (int r = 0; r < 2; r++) {
                float cy = (110 + r * 56) * s;
                int v = prefs.getInt(r == 0 ? "musV" : "sfxV", 10);
                if (in(x, y, cx + 110 * s, cy, 40 * s, 34 * s)) v = Math.max(0, v - 1);
                else if (in(x, y, cx + 250 * s, cy, 40 * s, 34 * s)) v = Math.min(10, v + 1);
                else continue;
                prefs.edit().putInt(r == 0 ? "musV" : "sfxV", v).apply();
                audio.musicVol = prefs.getInt("musV", 10) / 10f;
                audio.sfxVol = prefs.getInt("sfxV", 10) / 10f;
                audio.play(Audio.CLICK, 0.8f);
            }
            if (in(x, y, cx + 180 * s, 222 * s, 150 * s, 34 * s)) {
                Fx.shakeOn = !Fx.shakeOn;
                prefs.edit().putBoolean("shake", Fx.shakeOn).apply();
                audio.play(Audio.CLICK, 0.7f);
            }
            if (in(x, y, cx, 296 * s, 300 * s, 34 * s)) {
                if (!resetArm) resetArm = true;
                else { SaveGame.clear(prefs); resetArm = false; }
                audio.play(Audio.CLICK, 0.7f);
            }
        } else if (screen == INTRO) {
            if (introT > 0.6f) beginPlay();
        } else if (screen == DEAD) {
            if (deadT < 0.8f) return;
            if (in(x, y, W / 2f - 130 * s, 330 * s, 220 * s, 46 * s)) {
                selCls = lastCls; selDiff = lastDiff;
                startRun();
            } else if (in(x, y, W / 2f + 130 * s, 330 * s, 220 * s, 46 * s)) {
                audio.play(Audio.CLICK, 0.7f);
                toTitle();
            }
        }
    }

    private void cardTap(float x, float y) {
        if (lvlT < 0.45f) return;
        for (int i = 0; i < 3; i++) {
            if (in(x, y, W / 2f + (i - 1) * 218 * S, 205 * S, 200 * S, 250 * S)) {
                game.chooseCard(i);
                resetInput();
                return;
            }
        }
    }

    // ---------------- frame ----------------

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : (now - lastNanos) / 1e9f;
        lastNanos = now;
        if (dt > 0.05f) dt = 0.05f;
        uiT += dt;
        frameDt = dt;
        if (W == 0) { postInvalidateOnAnimation(); return; }

        interactLabel = screen == PLAY ? game.interactLabel() : null;
        if (screen == PLAY) {
            if (game.levelUpPending) {
                lvlT += dt;
            } else {
                lvlT = 0f;
                Game.Ctl k = game.ctl;
                k.left = bDown[B_L]; k.right = bDown[B_R];
                k.crouchHeld = crouchLatch;
                k.chargeHeld = bDown[B_L] && bDown[B_R] && !bSwiped[B_L] && !bSwiped[B_R] && !crouchLatch;
                k.atkHeld = bDown[B_ATK] || bDown[B_ATK2];
                k.dualAtk = bDown[B_ATK] && bDown[B_ATK2];
                game.viewW = W / (S * Game.ZOOM); game.viewH = H / (S * Game.ZOOM);
                game.update(dt);
                if (game.levelUpPending) lvlT = 0f;
                if (game.stage != lastSavedStage && !game.over) { lastSavedStage = game.stage; SaveGame.save(prefs, game); }
                if (game.over) {
                    SaveGame.clear(prefs);
                    screen = DEAD;
                    deadT = 0f;
                    if (game.stage > bestStage || (game.stage == bestStage && game.totalKills > bestKills)) {
                        bestStage = game.stage; bestKills = game.totalKills;
                        prefs.edit().putInt("bestStage", bestStage).putInt("bestKills", bestKills).apply();
                    }
                    resetInput();
                }
            }
        } else if (screen == DEAD) {
            deadT += dt;
        }
        if (isMenu()) { game.viewW = W / (S * Game.ZOOM); game.viewH = H / (S * Game.ZOOM); }

        if (screen == INTRO) {
            introT += dt;
            drawIntro(c);
            if (introT > 7.0f) beginPlay();
            postInvalidateOnAnimation();
            return;
        }

        // ---- draw ----
        if (screen == SPLASH) {
            splashT += dt;
            drawSplash(c, dt);
            postInvalidateOnAnimation();
            return;
        }
        render.draw(c, game, W, H, S, uiT);
        if (screen == BEST) {
            drawBest(c);
        } else if (screen == TITLE) {
            drawTitle(c);
        } else if (screen == SELECT) {
            drawSelect(c);
        } else if (screen == SETTINGS) {
            drawSettings(c);
        } else {
            drawHud(c);
            if (screen == PLAY && game.levelUpPending) drawLevelUp(c);
            else if (screen == PAUSE) drawPause(c);
            else if (screen == DEAD) drawDead(c);
        }
        postInvalidateOnAnimation();
    }

    private final float[] ipose = new float[Rig.N];

    private void caption(Canvas c, String t, float t0, float t1, float y) {
        float a = Math.min(1f, Math.min((introT - t0) * 3f, (t1 - introT) * 3f));
        if (a <= 0f) return;
        txt(c, t, W / 2f, y, 17, Render.a(0xFFFFFFFF, a), Paint.Align.CENTER, true);
    }

    private void drawIntro(Canvas c) {
        float s = S, T = introT;
        int cls = game.p.cls;
        int col = game.p.color();
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0xFF05060F);
        c.drawRect(0, 0, W, H, pt);
        for (int i = 0; i < 70; i++) {
            float sx = ((i * 7919) % 1000) / 1000f * W, sy = ((i * 104729) % 1000) / 1000f * H * 0.8f;
            float tw = 0.4f + 0.6f * (float) Math.abs(Math.sin(T * 2f + i));
            pt.setColor(Render.a(0xFFFFFFFF, 0.5f * tw));
            c.drawCircle(sx, sy, (1f + (i % 3)) * s * 0.6f, pt);
        }
        float gy = H * 0.74f;
        float fa = 1f;
        float fx = W * 0.5f, fy = gy, sc = 2.3f * s;
        float flash = 0f;
        if (cls == 0) {
            // ґрунт
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeWidth(2.5f * s);
            pt.setColor(Render.a(0xFF4DD2FF, 0.8f));
            c.drawLine(0, gy, W, gy, pt);
            float wx = W * 0.5f;
            if (T < 2.4f) {
                fx = W * 0.12f + (wx - W * 0.12f) * (T / 2.4f);
                Rig.run(ipose, T * 9f);
            } else {
                fx = wx;
                Rig.copy(ipose, T < 3.3f ? Rig.FALL : Rig.JUMP);
            }
            if (T > 2.3f) {
                float k = Math.min(1f, (T - 2.3f) / 0.9f);
                for (int i = 0; i < 4; i++) {
                    float r = (50 + i * 22) * s * k * (1f + 0.06f * (float) Math.sin(T * 8f + i));
                    rf.set(wx - r * 1.5f, gy - r * 0.25f, wx + r * 1.5f, gy + r * 0.25f);
                    pt.setStrokeWidth(3f * s);
                    pt.setColor(Render.a(i % 2 == 0 ? 0xFF4DFFB0 : 0xFF7DF9FF, 0.8f - i * 0.15f));
                    c.drawOval(rf, pt);
                }
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(Render.a(0xFF4DFFB0, 0.25f * k));
                rf.set(wx - 70 * s * k, gy - 12 * s * k, wx + 70 * s * k, gy + 12 * s * k);
                c.drawOval(rf, pt);
            }
            if (T > 3.4f) {
                float k = (T - 3.4f) / 1.0f;
                fy = gy + k * k * 230f * s;
                fa = Math.max(0f, 1f - k);
                sc *= Math.max(0.2f, 1f - k * 0.6f);
            }
            flash = T > 4.2f ? Math.max(0f, 1f - (T - 4.2f) * 1.2f) : 0f;
        } else {
            float cx = W * 0.74f;
            float bob = (float) Math.sin(T * 2.4f) * 5f * s;
            fx = W * 0.32f; fy = H * 0.56f + bob;
            Rig.copy(ipose, Rig.CHARGE);
            if (T > 1.8f) {
                float k = Math.min(1f, (T - 1.8f) / 1.2f);
                pt.setStyle(Paint.Style.STROKE);
                pt.setStrokeCap(Paint.Cap.ROUND);
                for (int pass = 0; pass < 2; pass++) {
                    pt.setStrokeWidth((pass == 0 ? 9f : 3f) * s);
                    pt.setColor(pass == 0 ? Render.a(0xFF7B5CFF, 0.5f) : 0xFFFFFFFF);
                    float px = cx, py = H * 0.15f;
                    for (int i = 0; i < 9; i++) {
                        if (i / 9f > k) break;
                        float nx = cx + (float) Math.sin(i * 2.3f) * 26f * s, ny = H * 0.15f + i * H * 0.08f;
                        c.drawLine(px, py, nx, ny, pt);
                        px = nx; py = ny;
                    }
                }
            }
            if (T > 3.1f) {
                float k = Math.min(1f, (T - 3.1f) / 1.3f);
                fx = W * 0.32f + (cx - W * 0.32f) * k * k;
                fy = H * 0.56f - (H * 0.12f) * k + bob * (1f - k);
                sc *= Math.max(0.15f, 1f - k * 0.85f);
                fa = Math.max(0f, 1f - k * 0.9f);
            }
            flash = T > 4.3f ? Math.max(0f, 1f - (T - 4.3f) * 1.2f) : 0f;
        }
        if (fa > 0.02f) {
            c.save();
            c.translate(fx, fy);
            c.scale(sc, sc);
            if (cls == 1 && T > 3.1f) c.rotate((T - 3.1f) * 420f);
            Rig.draw(c, pt, 0f, 0f, 1, ipose, col, fa, true, game.p.hairLen(), game.p.hairColor());
            c.restore();
        }
        if (cls == 0) {
            caption(c, "Кай просто йшов додому…", 0.3f, 2.5f, H * 0.9f);
            caption(c, "…коли земля під ногами засяяла.", 2.6f, 4.4f, H * 0.9f);
        } else {
            caption(c, "Рей медитував серед зірок…", 0.3f, 2.5f, H * 0.9f);
            caption(c, "…доки сам простір не тріснув.", 2.6f, 4.4f, H * 0.9f);
        }
        if (T > 4.6f) {
            float a = Math.min(1f, (T - 4.6f) * 2f);
            float out = T > 6.4f ? Math.max(0f, 1f - (T - 6.4f) * 1.6f) : 1f;
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(Render.a(0xFF05060F, 0.88f * a * out));
            c.drawRect(0, 0, W, H, pt);
            txt(c, "OURAVEX", W / 2f, H * 0.46f, 56, Render.a(col, a * out), Paint.Align.CENTER, true);
            txt(c, "Світ без кінця. Вижити можна лише б'ючись.", W / 2f, H * 0.58f, 15, Render.a(0xFFFFFFFF, a * out), Paint.Align.CENTER, true);
        }
        if (flash > 0f) {
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(Render.a(0xFFFFFFFF, flash));
            c.drawRect(0, 0, W, H, pt);
        }
        if (T > 0.8f) txt(c, "торкнись, щоб пропустити", W - 14 * s, H - 12 * s, 10, 0x88FFFFFF, Paint.Align.RIGHT, false);
    }

    private void drawUpdate(Canvas c) {
        int st = Updater.state;
        if (st == Updater.IDLE) return;
        String t;
        int col = 0xFF4DFFB0;
        if (st == Updater.AVAILABLE) t = "ОНОВЛЕННЯ " + Updater.name + " · ТОРКНИСЬ";
        else if (st == Updater.DOWNLOADING) t = "Завантаження " + (int) (Updater.progress * 100) + "%";
        else if (st == Updater.INSTALLING) t = "Встановлення…";
        else { t = Updater.msg; col = 0xFFFF6A7A; }
        panel(c, 150 * S, 26 * S, 270 * S, 36 * S, col, 0.18f);
        txt(c, t, 150 * S, 31 * S, 11, col, Paint.Align.CENTER, true);
    }

    // ---------------- drawing helpers ----------------

    private void txt(Canvas c, String s, float x, float y, float size, int col, Paint.Align al, boolean bold) {
        pt.setStyle(Paint.Style.FILL);
        pt.setTextAlign(al);
        pt.setTextSize(size * S * (screen == PLAY ? 1.3f : 1f));
        pt.setFakeBoldText(bold);
        pt.setColor(Render.a(0xFF000000, 0.55f * ((col >>> 24) / 255f)));
        c.drawText(s, x + 1.2f * S, y + 1.2f * S, pt);
        pt.setColor(col);
        c.drawText(s, x, y, pt);
        pt.setFakeBoldText(false);
        pt.setTextAlign(Paint.Align.LEFT);
    }

    private final float[] bvLast = new float[4], bvLo = new float[4], bvHi = new float[4], bvGlow = new float[4];
    private final boolean[] bvUp = new boolean[4], bvInit = new boolean[4];
    private float frameDt = 0.016f;

    /** Смужка з розжареним шматком: приріст/втрата спалахує й гасне. */
    private void barG(int id, Canvas c, float x, float y, float w, float h, float frac, int col) {
        frac = Math.max(0f, Math.min(1f, frac));
        if (!bvInit[id]) { bvInit[id] = true; bvLast[id] = frac; }
        float last = bvLast[id];
        if (frac < last - 0.0004f) {
            bvHi[id] = (bvGlow[id] > 0.05f && !bvUp[id]) ? Math.max(bvHi[id], last) : last;
            bvLo[id] = frac; bvUp[id] = false; bvGlow[id] = 1f;
        } else if (frac > last + 0.0004f) {
            bvLo[id] = (bvGlow[id] > 0.05f && bvUp[id]) ? bvLo[id] : last;
            bvHi[id] = frac; bvUp[id] = true; bvGlow[id] = 1f;
        }
        bvLast[id] = frac;
        bvGlow[id] = Math.max(0f, bvGlow[id] - frameDt * 1.5f);
        float gl = bvGlow[id];
        float rr = h / 2f;
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0x99000000);
        rf.set(x, y, x + w, y + h);
        c.drawRoundRect(rf, rr, rr, pt);
        if (gl > 0.01f && !bvUp[id]) {
            // втрата: розжарений шматок, що гасне
            float x0 = x + w * frac, x1 = x + w * Math.max(frac, bvHi[id]);
            pt.setColor(Render.a(0xFFFF6A2A, 0.35f * gl));
            rf.set(x0 - 2 * S, y - 3 * S, x1 + 2 * S, y + h + 3 * S);
            c.drawRoundRect(rf, rr, rr, pt);
            pt.setColor(Render.a(0xFFFFE9B0, gl));
            rf.set(x0, y, x1, y + h);
            c.drawRoundRect(rf, rr, rr, pt);
        }
        if (frac > 0f) {
            float lo = (gl > 0.01f && bvUp[id]) ? Math.min(frac, bvLo[id]) : frac;
            pt.setColor(Render.a(col, 0.35f));
            rf.set(x - 1.5f * S, y - 1.5f * S, x + w * lo + 1.5f * S, y + h + 1.5f * S);
            c.drawRoundRect(rf, rr, rr, pt);
            pt.setColor(col);
            rf.set(x, y, x + w * lo, y + h);
            c.drawRoundRect(rf, rr, rr, pt);
            if (lo < frac) {
                // приріст: нова ділянка розжарена, потім остигає до кольору смужки
                float x0 = x + w * lo, x1 = x + w * frac;
                pt.setColor(Render.a(col, 0.4f * gl));
                rf.set(x0 - 3 * S, y - 3 * S, x1 + 3 * S, y + h + 3 * S);
                c.drawRoundRect(rf, rr, rr, pt);
                int mix = Render.a(0xFFFFFFFF, gl);
                pt.setColor(col);
                rf.set(x0, y, x1, y + h);
                c.drawRoundRect(rf, rr, rr, pt);
                pt.setColor(mix);
                c.drawRoundRect(rf, rr, rr, pt);
            }
        }
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(1.2f * S);
        pt.setColor(0x55FFFFFF);
        rf.set(x, y, x + w, y + h);
        c.drawRoundRect(rf, rr, rr, pt);
    }

    private void bar(Canvas c, float x, float y, float w, float h, float frac, int col) {
        frac = Math.max(0f, Math.min(1f, frac));
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0x99000000);
        rf.set(x, y, x + w, y + h);
        c.drawRoundRect(rf, h / 2, h / 2, pt);
        if (frac > 0f) {
            pt.setColor(Render.a(col, 0.35f));
            rf.set(x - 1.5f * S, y - 1.5f * S, x + w * frac + 1.5f * S, y + h + 1.5f * S);
            c.drawRoundRect(rf, h / 2, h / 2, pt);
            pt.setColor(col);
            rf.set(x, y, x + w * frac, y + h);
            c.drawRoundRect(rf, h / 2, h / 2, pt);
        }
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(1.2f * S);
        pt.setColor(0x55FFFFFF);
        rf.set(x, y, x + w, y + h);
        c.drawRoundRect(rf, h / 2, h / 2, pt);
    }

    private void panel(Canvas c, float cx, float cy, float w, float h, int col, float fillA) {
        rf.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(Render.a(0xFF0A0A1C, fillA));
        c.drawRoundRect(rf, 10 * S, 10 * S, pt);
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(5 * S);
        pt.setColor(Render.a(col, 0.18f));
        c.drawRoundRect(rf, 10 * S, 10 * S, pt);
        pt.setStrokeWidth(2 * S);
        pt.setColor(col);
        c.drawRoundRect(rf, 10 * S, 10 * S, pt);
    }

    private void wrapText(Canvas c, String s, float cx, float y, float maxW, float size, int col) {
        pt.setTextSize(size * S);
        pt.setFakeBoldText(false);
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        float yy = y;
        for (String w : words) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (pt.measureText(test) > maxW && line.length() > 0) {
                txt(c, line.toString(), cx, yy, size, col, Paint.Align.CENTER, false);
                yy += size * 1.25f * S;
                line.setLength(0);
                line.append(w);
            } else {
                line.setLength(0);
                line.append(test);
            }
        }
        if (line.length() > 0) txt(c, line.toString(), cx, yy, size, col, Paint.Align.CENTER, false);
    }

    private static String clock(float t) {
        int s = (int) t;
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    // ---------------- title ----------------

    private static final String[] BTABS = {"МОБИ", "ПРЕДМЕТИ", "КАМЕНІ", "ПАСИВНІ", "УМІННЯ"};

    private int bestCount(int tab) {
        switch (tab) {
            case 0: return 7;
            case 1: return Data.ITEMS;
            case 2: return Data.GEMS;
            case 3: return Data.NPAS;
            default: return 14;
        }
    }

    private boolean bestKnown(int tab, int i) {
        switch (tab) {
            case 0: return Codex.has(i < 3 ? "m" + i : "b" + (i - 3));
            case 1: return Codex.has("i" + i);
            case 2: return Codex.has("g" + i);
            case 3: return Codex.has("p" + i);
            default: return Codex.has("s" + (i / 7) + "_" + (i % 7));
        }
    }

    private int bestColor(int tab, int i) {
        switch (tab) {
            case 0: return i < 3 ? new int[]{0xFFFF4D6D, 0xFFB06BFF, 0xFFFFB020}[i] : new int[]{0xFFFF3A3A, 0xFF9A5CFF, 0xFFFF8A2A, 0xFF3AE0C8}[i - 3];
            case 1: return Data.ITEM_COL[i];
            case 2: return Data.GEM_COL[i];
            case 3: return Data.PAS_COL[i];
            default: return Data.SKILL_COL[i % 7];
        }
    }

    private String bestName(int tab, int i) {
        switch (tab) {
            case 0: return i < 3 ? Data.MOB_NAME[i] : "БОС: " + Data.BOSS_NAME[i - 3 == 0 ? 0 : (i - 3 == 1 ? 1 : (i - 3 == 2 ? 2 : 3))];
            case 1: return Data.ITEM_NAME[i];
            case 2: return Data.GEM_NAME[i];
            case 3: return Data.PAS_NAME[i];
            default: return Data.CLS_NAME[i / 7] + ": " + Data.SKILL_NAME[i / 7][i % 7];
        }
    }

    private String bestDesc(int tab, int i) {
        switch (tab) {
            case 0: return i < 3 ? Data.MOB_DESC[i] : Data.BOSS_DESC[i - 3];
            case 1: return Data.ITEM_DESC[i];
            case 2: return Data.GEM_DESC[i];
            case 3: return Data.PAS_DESC[i];
            default: return Data.SKILL_DESC[i / 7][i % 7];
        }
    }

    private void drawBest(Canvas c) {
        float s = S, Wv = wv();
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0xF2050614);
        c.drawRect(0, 0, W, H, pt);
        txt(c, "БЕСТІАРІЙ", W / 2f, 24 * s, 18, 0xFFB06BFF, Paint.Align.CENTER, true);
        for (int t = 0; t < 5; t++) {
            float cx = W / 2f + (t - 2) * 110 * s;
            boolean on = bestTab == t;
            panel(c, cx, 46 * s, 104 * s, 22 * s, on ? 0xFFFFD84A : 0xFF4A5278, on ? 0.95f : 0.55f);
            txt(c, BTABS[t], cx, 50 * s, 10, on ? 0xFFFFD84A : 0xFF8A93B8, Paint.Align.CENTER, true);
        }
        panel(c, W / 2f, 206 * s, Wv * s - 40 * s, 290 * s, 0xFFB06BFF, 0.9f);
        int n = bestCount(bestTab);
        int known = 0;
        float cell = 42 * s, gap = 6 * s;
        for (int i = 0; i < n; i++) {
            boolean k = bestKnown(bestTab, i);
            if (k) known++;
            float x = 40 * s + (i % 8) * (cell + gap), y = 78 * s + (i / 8) * (cell + gap);
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(Render.a(0xFF141A38, 0.95f));
            rf.set(x, y, x + cell, y + cell);
            c.drawRoundRect(rf, 6 * s, 6 * s, pt);
            int col = k ? bestColor(bestTab, i) : 0xFF3A3F5C;
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeWidth(1.8f * s);
            pt.setColor(Render.a(col, bestSel == i ? 1f : 0.6f));
            c.drawRoundRect(rf, 6 * s, 6 * s, pt);
            if (!k) txt(c, "?", x + cell / 2f, y + cell * 0.68f, 20, 0xFF5A6080, Paint.Align.CENTER, true);
            else if (bestTab == 2) gemShape(c, x + cell / 2f, y + cell / 2f, 14 * s, col);
            else txt(c, bestName(bestTab, i).replace("БОС: ", "").replace("КАЙ: ", "").replace("РЕЙ: ", "").substring(0, 1), x + cell / 2f, y + cell * 0.68f, 20, col, Paint.Align.CENTER, true);
        }
        txt(c, "Відкрито: " + known + " / " + n, 40 * s, 300 * s, 10, 0xFF8A93B8, Paint.Align.LEFT, false);
        float dx = 40 * s + 8 * (cell + gap) + 14 * s, dw = Wv * s - 40 * s - dx - 14 * s;
        panel(c, dx + dw / 2f, 196 * s, dw, 230 * s, 0xFF4A5278, 0.6f);
        if (bestSel >= 0 && bestSel < n) {
            boolean k = bestKnown(bestTab, bestSel);
            if (k) {
                int col = bestColor(bestTab, bestSel);
                txt(c, bestName(bestTab, bestSel), dx + dw / 2f, 100 * s, 14, col, Paint.Align.CENTER, true);
                wrapText(c, bestDesc(bestTab, bestSel), dx + dw / 2f, 128 * s, dw - 24 * s, 11, 0xFFB8C4FF);
            } else {
                txt(c, "???", dx + dw / 2f, 120 * s, 20, 0xFF5A6080, Paint.Align.CENTER, true);
                wrapText(c, "Ще не відкрито. Зустрінь або отримай це в грі.", dx + dw / 2f, 148 * s, dw - 24 * s, 11, 0xFF8A93B8);
            }
        } else {
            txt(c, "Торкнись запису", dx + dw / 2f, 200 * s, 12, 0xFF8A93B8, Paint.Align.CENTER, false);
        }
        panel(c, W / 2f, 376 * s, 160 * s, 28 * s, 0xFF4DFFB0, 0.9f);
        txt(c, "НАЗАД", W / 2f, 382 * s, 14, 0xFF4DFFB0, Paint.Align.CENTER, true);
    }

    private void bestTap(float x, float y) {
        float s = S;
        for (int t = 0; t < 5; t++) {
            if (in(x, y, W / 2f + (t - 2) * 110 * s, 46 * s, 104 * s, 22 * s)) { bestTab = t; bestSel = -1; audio.play(Audio.CLICK, 0.5f); return; }
        }
        float cell = 42 * s, gap = 6 * s;
        for (int i = 0; i < bestCount(bestTab); i++) {
            float cx = 40 * s + (i % 8) * (cell + gap) + cell / 2f, cy = 78 * s + (i / 8) * (cell + gap) + cell / 2f;
            if (in(x, y, cx, cy, cell, cell)) { bestSel = i; audio.play(Audio.CLICK, 0.5f); return; }
        }
        if (in(x, y, W / 2f, 376 * s, 160 * s, 28 * s)) { screen = TITLE; audio.play(Audio.CLICK, 0.6f); }
    }

    private String ver() { return "1." + System.getProperty("ouravex.gamecode", "0"); }

    private void streaks(Canvas c, float alpha) {
        pt.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 36; i++) {
            float sp = 40f + (i * 37 % 90);
            float x = ((i * 211.7f + uiT * sp) % (W + 200f * S)) - 100f * S;
            float y = ((i * 97.3f) % H);
            float len = (30f + (i * 13 % 70)) * S;
            int col = i % 3 == 0 ? 0xFF35D6FF : (i % 3 == 1 ? 0xFFB06BFF : 0xFFFF4D8D);
            pt.setColor(Render.a(col, alpha * (0.25f + 0.25f * (i % 4) / 3f)));
            c.drawRect(x, y, x + len, y + 1.6f * S, pt);
        }
    }

    private void drawSplash(Canvas c, float dt) {
        float s = S;
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0xFF050614);
        c.drawRect(0, 0, W, H, pt);
        float pulse = 0.5f + 0.5f * (float) Math.sin(uiT * 2f);
        pt.setColor(Render.a(0xFF35D6FF, 0.07f + 0.03f * pulse));
        c.drawCircle(W / 2f, 150 * s, 190 * s, pt);
        pt.setColor(Render.a(0xFFB06BFF, 0.06f));
        c.drawCircle(W / 2f + 90 * s, 170 * s, 130 * s, pt);
        streaks(c, 1f);
        float rr = (splashT * 160f) % 260f;
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(2f * s);
        pt.setColor(Render.a(0xFF35D6FF, Math.max(0f, 0.35f - rr / 800f)));
        c.drawCircle(W / 2f, 140 * s, rr * s, pt);
        String name = "OURAVEX";
        for (int i = 0; i < name.length(); i++) {
            float a = Math.max(0f, Math.min(1f, (splashT - 0.12f * i) * 3f));
            float lx = W / 2f + (i - 3) * 46 * s, ly = (150 + (1f - a) * 24f) * s;
            txt(c, name.substring(i, i + 1), lx, ly, 62, Render.a(0xFF35D6FF, a * (0.35f + 0.1f * pulse)), Paint.Align.CENTER, true);
            txt(c, name.substring(i, i + 1), lx, ly - 2 * s, 62, Render.a(0xFFFFFFFF, a), Paint.Align.CENTER, true);
        }
        txt(c, "Бий. Телепортуйся. Виживай.", W / 2f, 186 * s, 13, Render.a(0xFF9AB0D0, Math.min(1f, Math.max(0f, splashT - 1f))), Paint.Align.CENTER, false);

        int st = Updater.state;
        boolean fin = Updater.finished || splashT > 9f;
        float target;
        String status;
        if (st == Updater.AVAILABLE) {
            if (!Updater.isApk()) { Updater.start(getContext()); }
            target = 0.6f;
            status = "Знайдено оновлення " + Updater.name;
        } else if (st == Updater.DOWNLOADING) {
            target = 0.6f + 0.4f * Updater.progress;
            status = "Завантаження оновлення " + (int) (Updater.progress * 100) + "%";
        } else if (st == Updater.INSTALLING) {
            target = 1f;
            status = Updater.isApk() ? "Встановлення…" : "Перезапуск гри…";
        } else if (st == Updater.ERROR) {
            target = 1f;
            status = Updater.msg;
        } else if (!fin) {
            target = Math.min(0.55f, splashT * 0.15f);
            status = "Перевірка оновлень…";
        } else {
            target = 1f;
            status = Updater.offline ? "Немає інтернету. Запуск без оновлень" : "Оновлень немає · версія " + ver();
        }
        if (splashT < 2.2f) target = Math.min(target, splashT / 2.2f * 0.9f);
        splashBar += (target - splashBar) * Math.min(1f, dt * 4f);
        float bw = 300f, by = 330f;
        boolean apk = st == Updater.AVAILABLE && Updater.isApk();
        txt(c, apk ? "Потрібен новий лаунчер гри" : status, W / 2f, 318 * s, 11, st == Updater.ERROR ? 0xFFFF6A7A : 0xFF9AB0D0, Paint.Align.CENTER, false);
        boolean ready = splashBar > 0.99f && st != Updater.DOWNLOADING && st != Updater.INSTALLING;
        if (!ready || apk) {
            barG(3, c, W / 2f - bw / 2f * s, by * s + 8 * s, bw * s, 8 * s, Math.max(0.02f, splashBar), st == Updater.DOWNLOADING ? 0xFF4DFFB0 : 0xFF35D6FF);
        }
        if (apk) button(c, W / 2f / s, 300f, 260f, 34f, "ВСТАНОВИТИ ОНОВЛЕННЯ", 0xFFFFD84A, true);
        if (ready) {
            float pu = 0.9f + 0.1f * pulse;
            panel(c, W / 2f, 350 * s, 220 * s, 40 * s, 0xFF4DFFB0, 0.9f);
            txt(c, "ГРАТИ", W / 2f, 358 * s, 20 * pu, 0xFF4DFFB0, Paint.Align.CENTER, true);
        }
        txt(c, ver(), W - 12 * s, H - 8 * s, 9, 0xFF5A6690, Paint.Align.RIGHT, false);
    }

    private void drawTitle(Canvas c) {
        float s = S;
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0xB8050614);
        c.drawRect(0, 0, W, H, pt);
        pt.setColor(0x66000008);
        c.drawRect(0, 0, 300 * s, H, pt);
        streaks(c, 0.7f);
        float pulse = 0.5f + 0.5f * (float) Math.sin(uiT * 2f);
        txt(c, "OURAVEX", 24 * s, 66 * s, 44, Render.a(0xFF35D6FF, 0.35f + 0.1f * pulse), Paint.Align.LEFT, true);
        txt(c, "OURAVEX", 24 * s, 64 * s, 44, 0xFFFFFFFF, Paint.Align.LEFT, true);
        txt(c, "Бий. Телепортуйся. Виживай.", 26 * s, 86 * s, 11, 0xFF9AB0D0, Paint.Align.LEFT, false);
        boolean has = SaveGame.exists(prefs);
        String[] lb = {"ПРОДОВЖИТИ", "НОВА ГРА", "БЕСТІАРІЙ", "НАЛАШТУВАННЯ"};
        int[] cl = {0xFF4DFFB0, 0xFFFFD84A, 0xFFB06BFF, 0xFF35D6FF};
        for (int i = 0; i < 4; i++) {
            boolean off = i == 0 && !has;
            int col = off ? 0xFF3A3F5C : cl[i];
            panel(c, 150 * s, (150 + i * 46) * s, 236 * s, 38 * s, col, off ? 0.5f : 0.85f);
            txt(c, lb[i], 150 * s, (150 + i * 46 + (i == 0 && has ? -1 : 5)) * s, 16, off ? 0xFF5A6080 : col, Paint.Align.CENTER, true);
            if (i == 0 && has) txt(c, SaveGame.summary(prefs), 150 * s, (150 + 12) * s, 8, 0xFF9AB0D0, Paint.Align.CENTER, false);
        }
        for (int i = 0; i < 2; i++) {
            float cx = wv() * s - (200 - i * 110) * s, gy = 330 * s;
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(Render.a(Data.CLS_COL[i], 0.10f + 0.04f * pulse));
            c.drawCircle(cx, gy - 110 * s, 96 * s, pt);
            float[] pose = prevPose;
            Rig.copy(pose, Rig.IDLE);
            float b = (float) Math.sin(uiT * 3.5f + i);
            pose[1] += b * 1.2f; pose[6] += b; pose[8] += b;
            if (i == 1) { pose[5] = 44; pose[6] = -60; pose[7] = 38; pose[8] = -56; }
            c.save();
            c.translate(cx, gy);
            c.scale(s * 1.9f, s * 1.9f);
            Rig.draw(c, pt, 0, 0, i == 0 ? 1 : -1, pose, Data.CLS_COL[i], 1f, true, i == 0 ? 2f : 0f, Data.CLS_HAIR[i]);
            c.restore();
        }
        if (bestStage > 0) txt(c, "Рекорд: локація " + bestStage + " · вбито " + bestKills, 24 * s, 384 * s, 10, 0xFF8890B0, Paint.Align.LEFT, false);
        txt(c, "Версія " + ver(), W - 12 * s, H - 8 * s, 9, 0xFF5A6690, Paint.Align.RIGHT, false);
    }

    private void drawSettings(Canvas c) {
        float s = S, cx = W / 2f;
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0xE6050614);
        c.drawRect(0, 0, W, H, pt);
        streaks(c, 0.5f);
        button(c, 70f, 26f, 110f, 30f, "‹ НАЗАД", 0xFFB8C4FF, true);
        txt(c, "НАЛАШТУВАННЯ", cx, 46 * s, 24, 0xFFFFFFFF, Paint.Align.CENTER, true);
        String[] nm = {"МУЗИКА", "ЗВУКИ"};
        for (int r = 0; r < 2; r++) {
            float cy = (110 + r * 56) * s;
            int v = prefs.getInt(r == 0 ? "musV" : "sfxV", 10);
            panel(c, cx, cy, 560 * s, 44 * s, 0xFF35D6FF, 0.6f);
            txt(c, nm[r], cx - 260 * s, cy + 5 * s, 14, 0xFFFFFFFF, Paint.Align.LEFT, true);
            button(c, cx / s + 110f, cy / s, 40f, 30f, "−", 0xFF35D6FF, true);
            button(c, cx / s + 250f, cy / s, 40f, 30f, "+", 0xFF35D6FF, true);
            for (int k = 0; k < 10; k++) {
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(k < v ? Render.a(0xFF4DFFB0, 0.9f) : 0x33607098);
                rf.set(cx + (135 + k * 11) * s, cy - 9 * s, cx + (143 + k * 11) * s, cy + 9 * s);
                c.drawRoundRect(rf, 2 * s, 2 * s, pt);
            }
        }
        panel(c, cx, 222 * s, 560 * s, 44 * s, 0xFF35D6FF, 0.6f);
        txt(c, "ТРЯСКА ЕКРАНА", cx - 260 * s, 227 * s, 14, 0xFFFFFFFF, Paint.Align.LEFT, true);
        button(c, cx / s + 180f, 222f, 150f, 34f, Fx.shakeOn ? "УВІМКНЕНО" : "ВИМКНЕНО", Fx.shakeOn ? 0xFF4DFFB0 : 0xFFFF4D6D, true);
        boolean has = SaveGame.exists(prefs);
        button(c, cx / s, 296f, 300f, 34f, resetArm ? "ТОЧНО? ТОРКНИСЬ ЩЕ РАЗ" : (has ? "ВИДАЛИТИ ЗБЕРЕЖЕННЯ" : "ЗБЕРЕЖЕНЬ НЕМАЄ"), has ? 0xFFFF4D6D : 0xFF5A6080, has);
        txt(c, "Версія " + ver(), cx, 372 * s, 10, 0xFF5A6690, Paint.Align.CENTER, false);
    }

    private void drawSelect(Canvas c) {
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0xCC060616);
        c.drawRect(0, 0, W, H, pt);
        float s = S;
        float pulse = 0.5f + 0.5f * (float) Math.sin(uiT * 2f);
        button(c, 70f, 26f, 110f, 30f, "‹ НАЗАД", 0xFFB8C4FF, true);
        txt(c, "ОБЕРИ ГЕРОЯ", W / 2f, 46 * s, 24, 0xFFFFFFFF, Paint.Align.CENTER, true);
        for (int i = 0; i < 2; i++) {
            float cx = W / 2f + (i == 0 ? -118 : 118) * s, cy = 160 * s;
            boolean sel = selCls == i;
            panel(c, cx, cy, 220 * s, 150 * s, sel ? Data.CLS_COL[i] : 0xFF4A5070, sel ? 0.85f : 0.6f);
            // preview figure
            float[] pose = prevPose;
            Rig.copy(pose, Rig.IDLE);
            float b = (float) Math.sin(uiT * 4f);
            pose[1] += b * 1.2f;
            pose[6] += b; pose[8] += b;
            if (i == 1) { pose[5] = 44; pose[6] = -60; pose[7] = 38; pose[8] = -56; }
            c.save();
            c.translate(cx - 62 * s, cy + 52 * s);
            c.scale(s * 1.15f, s * 1.15f);
            if (sel) {
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(Render.a(Data.CLS_COL[i], 0.12f));
                c.drawCircle(0, -45, 62, pt);
            }
            Rig.draw(c, pt, 0, 0, 1, pose, Data.CLS_COL[i], sel ? 1f : 0.6f, true, i == 0 ? 2f : 0f, Data.CLS_HAIR[i]);
            c.restore();
            txt(c, Data.CLS_NAME[i], cx + 36 * s, cy - 38 * s, 24, sel ? Data.CLS_COL[i] : 0xFF8890B0, Paint.Align.CENTER, true);
            txt(c, Data.CLS_ROLE[i], cx + 36 * s, cy - 18 * s, 12, 0xFFFFFFFF, Paint.Align.CENTER, false);
            wrapText(c, Data.CLS_DESC[i], cx + 36 * s, cy + 6 * s, 112 * s, 10, 0xFFB0B8D8);
        }
        for (int i = 0; i < 3; i++) {
            float cx = W / 2f + (i - 1) * 128 * s;
            boolean sel = selDiff == i;
            int col = i == 0 ? 0xFF4DFF88 : (i == 1 ? 0xFFFFD84A : 0xFFFF4D6D);
            panel(c, cx, 268 * s, 116 * s, 34 * s, sel ? col : 0xFF4A5070, sel ? 0.9f : 0.5f);
            txt(c, Data.DIFF_NAME[i], cx, 273 * s, 14, sel ? col : 0xFF8890B0, Paint.Align.CENTER, true);
        }
        float pu = 0.85f + 0.15f * pulse;
        panel(c, W / 2f, 332 * s, 240 * s, 48 * s, 0xFF4DFFB0, 0.9f);
        txt(c, "ПОЧАТИ", W / 2f, 340 * s, 22 * pu, 0xFF4DFFB0, Paint.Align.CENTER, true);
        if (SaveGame.exists(prefs)) txt(c, "Поточне збереження буде замінено", W / 2f, 372 * s, 10, 0xFF8890B0, Paint.Align.CENTER, false);
    }

    // ---------------- HUD ----------------

    private void drawHud(Canvas c) {
        Player p = game.p;
        float s = S;
        float hpF = p.hp / p.maxHp();
        if (hpF < 0.3f && p.st != Player.ST_DEAD) {
            float a = (0.18f + 0.12f * (float) Math.sin(uiT * 6f)) * (1f - hpF / 0.3f);
            pt.setStyle(Paint.Style.STROKE);
            pt.setColor(Render.a(0xFFFF2040, a));
            pt.setStrokeWidth(36 * s);
            c.drawRect(0, 0, W, H, pt);
        }
        // bars
        barG(0, c, 14 * s, 12 * s, 200 * s, 13 * s, hpF, hpF < 0.3f ? 0xFFFF4D6D : 0xFF4DFF88);
        txt(c, (int) p.hp + " / " + (int) p.maxHp(), 18 * s, 22.5f * s, 10, 0xFFFFFFFF, Paint.Align.LEFT, true);
        barG(1, c, 14 * s, 29 * s, 170 * s, 9 * s, p.ki / p.maxKi(), p.transformed ? Data.GOLD : 0xFF35D6FF);
        if (p.rageForm > 0f) barG(3, c, 14 * s, 42 * s, 140 * s, 6 * s, p.rageForm / 16f, Data.GOLD);
        else barG(3, c, 14 * s, 42 * s, 140 * s, 6 * s, p.rage / 100f, p.rage >= 100f ? 0xFFFFFFFF : 0xFFFF5A3C);
        txt(c, p.rageForm > 0f ? "ЛЮТЬ" : (p.rage >= 100f ? "ЛЮТЬ 100%: ЗАРЯДЖАЙ!" : "ЛЮТЬ " + (int) p.rage + "%"), 160 * s, 48 * s, 9, p.rage >= 100f ? 0xFFFFFFFF : 0xFFFF9A7A, Paint.Align.LEFT, true);
        barG(2, c, 0f, H - 6 * s, W, 6 * s, p.xp / p.xpNeed(), 0xFFFFD84A);
        for (int k = 0; k < 2; k++) {
            float px = W / 2f + (k == 0 ? -78 : 78) * s, py = H - 18 * s;
            int ps = p.passSlot[k];
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeWidth(2f * s);
            if (ps < 0) { pt.setColor(0x40B8C4FF); c.drawCircle(px, py, 9 * s, pt); }
            else {
                pt.setColor(Data.PAS_COL[ps]);
                c.drawCircle(px, py, 9 * s, pt);
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(Render.a(Data.PAS_COL[ps], 0.35f));
                c.drawCircle(px, py, 9 * s, pt);
                txt(c, String.valueOf(p.passLvl[ps]), px, py + 4 * s, 10, 0xFFFFFFFF, Paint.Align.CENTER, true);
            }
        }
        txt(c, "РІВ. " + p.level, 222 * s, 24 * s, 13, 0xFFFFD84A, Paint.Align.LEFT, true);
        // items
        float ix = 14 * s, iy = 56 * s;
        for (int i = 0; i < Data.ITEMS; i++) {
            if (p.items[i] <= 0) continue;
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(Render.a(Data.ITEM_COL[i], 0.9f));
            rf.set(ix, iy, ix + 13 * s, iy + 13 * s);
            c.drawRoundRect(rf, 3 * s, 3 * s, pt);
            txt(c, "x" + p.items[i], ix + 15 * s, iy + 11 * s, 10, 0xFFFFFFFF, Paint.Align.LEFT, true);
            ix += 40 * s;
            if (ix > 300 * s) { ix = 14 * s; iy += 17 * s; }
        }
        // top centre: stage + portal
        txt(c, "ЛОКАЦІЯ " + game.stage + "  ·  " + clock(game.runTime), W / 2f, 18 * s, 13, 0xFFFFFFFF, Paint.Align.CENTER, true);
        String ps;
        int pc = 0xFF7DF9FF;
        switch (game.portalState) {
            case 0: ps = "ПОРТАЛ  " + game.kills + "/" + game.killGoal + "  ·  " + clock(game.stageTime) + "/" + clock(game.timeGoal); break;
            case 1: ps = "ПОРТАЛ ГОТОВИЙ"; pc = 0xFF4DFFB0; break;
            case 2: ps = "ДОБИЙ ЗАЛИШКИ!"; pc = 0xFFFF9A3C; break;
            case 3: ps = ""; break;
            default: ps = "ПОРТАЛ ВІДКРИТО"; pc = 0xFFFFD84A; break;
        }
        if (game.altarState == 1) {
            txt(c, "ВИПРОБУВАННЯ " + game.altarKills + "/" + Game.ALTAR_GOAL + "  ·  " + (int) Math.ceil(game.altarT) + " с", W / 2f, 72 * s, 13, 0xFFB06BFF, Paint.Align.CENTER, true);
        }
        if (game.portalState == 0) {
            bar(c, W / 2f - 80 * s, 26 * s, 160 * s, 7 * s, Math.min(game.kills / (float) Math.max(1, game.killGoal), game.stageTime / game.timeGoal), 0xFF7DF9FF);
            txt(c, ps, W / 2f, 46 * s, 11, pc, Paint.Align.CENTER, true);
        } else if (!ps.isEmpty()) {
            txt(c, ps, W / 2f, 36 * s, 14, pc, Paint.Align.CENTER, true);
        }
        // boss bar
        if (game.boss != null) {
            float bw = 420 * s, bxx = W / 2f - bw / 2, byy = 58 * s;
            float f = game.boss.hp / game.boss.maxHp;
            bar(c, bxx, byy, bw, 12 * s, f, game.boss.phase == 1 ? 0xFFFF6A3A : 0xFFFF3050);
            txt(c, Data.BOSS_NAME[(game.boss.type == Ent.BOSS1 ? 0 : 1) + 2 * game.boss.bvar], W / 2f, byy + 26 * s, 13, 0xFFFFFFFF, Paint.Align.CENTER, true);
        }
        // combo
        if (p.combo >= 3 && p.comboT > 0f) {
            float pu = 1f + Math.min(0.4f, p.comboT > 2.0f ? (p.comboT - 2f) * 3f : 0f);
            txt(c, "x" + p.combo, W - 70 * s, 100 * s, 30 * pu, 0xFFFFD84A, Paint.Align.RIGHT, true);
            txt(c, "КОМБО", W - 70 * s, 116 * s, 11, 0xFFFFFFFF, Paint.Align.RIGHT, true);
        }
        // portal arrow
        if (game.portalState == 1 || game.portalState == 4) {
            float sx = (game.portalX - game.camX) * s * Game.ZOOM;
            if (sx < 0f || sx > W) {
                boolean left = sx < 0f;
                float ax = left ? 26 * s : W - 26 * s, ay = H * 0.38f;
                path.reset();
                path.moveTo(ax + (left ? -14 : 14) * s, ay);
                path.lineTo(ax + (left ? 10 : -10) * s, ay - 14 * s);
                path.lineTo(ax + (left ? 10 : -10) * s, ay + 14 * s);
                path.close();
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(Render.a(game.portalState == 4 ? 0xFFFFD84A : 0xFF4DFFB0, 0.6f + 0.4f * (float) Math.sin(uiT * 6f)));
                c.drawPath(path, pt);
                txt(c, "ПОРТАЛ", ax + (left ? 20 : -20) * s, ay + 34 * s, 10, 0xFFFFFFFF, left ? Paint.Align.LEFT : Paint.Align.RIGHT, true);
            }
        }
        // banner
        if (game.bannerT > 0f) {
            float a = Math.min(1f, game.bannerT * 1.5f);
            float sc = 1f + Math.max(0f, game.bannerT - 2.1f) * 1.2f;
            txt(c, game.banner, W / 2f, 128 * s, 28 * sc, Render.a(game.bannerCol, a), Paint.Align.CENTER, true);
        }
        drawControls(c);
        if (game.fx.flashA > 0f) {
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(Render.a(game.fx.flashColor, Math.min(0.9f, game.fx.flashA)));
            c.drawRect(0, 0, W, H, pt);
        }
    }

    private void circleBtn(Canvas c, int b, int col, boolean down, float fillA) {
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(Render.a(col, down ? fillA + 0.22f : fillA));
        c.drawCircle(bx[b], by[b], br[b], pt);
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(5 * S);
        pt.setColor(Render.a(col, 0.18f));
        c.drawCircle(bx[b], by[b], br[b], pt);
        pt.setStrokeWidth(2 * S);
        pt.setColor(Render.a(col, down ? 1f : 0.7f));
        c.drawCircle(bx[b], by[b], br[b], pt);
    }

    private void arrow(Canvas c, float cx, float cy, float r, int dir, int col) {
        path.reset();
        path.moveTo(cx + dir * r * 0.55f, cy);
        path.lineTo(cx - dir * r * 0.35f, cy - r * 0.5f);
        path.lineTo(cx - dir * r * 0.35f, cy + r * 0.5f);
        path.close();
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(col);
        c.drawPath(path, pt);
    }

    private void drawControls(Canvas c) {
        Player p = game.p;
        float s = S;
        int cc = p.color();
        for (int b = 0; b < 2; b++) {
            circleBtn(c, b, 0xFFB8C4FF, bDown[b], 0.10f);
            arrow(c, bx[b], by[b], br[b] * 0.8f, b == 0 ? -1 : 1, Render.a(0xFFFFFFFF, bDown[b] ? 1f : 0.7f));
        }
        // заряди ривка: смужка по центру внизу
        {
            int n = p.dashMax;
            float segW = 34 * s, gap = 5 * s, tw = n * segW + (n - 1) * gap;
            float x0 = W / 2f - tw / 2f - 14 * s, y0 = H - 22 * s;
            for (int i = 0; i < n; i++) {
                float frac = Math.max(0f, Math.min(1f, p.dashCharge - i));
                float xx = x0 + i * (segW + gap);
                bar(c, xx, y0, segW, 9 * s, frac, frac >= 1f ? 0xFF7DF9FF : 0xFF3A8FB0);
            }
            txt(c, "x" + (int) Math.floor(p.dashCharge + 0.001f), x0 + tw + 22 * s, y0 + 9 * s, 16, 0xFF7DF9FF, Paint.Align.CENTER, true);
        }
        if (bDown[B_L] && bDown[B_R] && !bSwiped[B_L] && !bSwiped[B_R]) {
            txt(c, "ЗАРЯДКА", (bx[B_L] + bx[B_R]) / 2f, by[B_L] - br[B_L] - 20 * s, 11, cc, Paint.Align.CENTER, true);
        }
        // attack
        for (int ab = 0; ab < 2; ab++) {
            int bb = ab == 0 ? B_ATK : B_ATK2;
            circleBtn(c, bb, cc, bDown[bb], 0.16f);
            glyphAttack(c, bx[bb], by[bb], br[bb] * 0.55f, cc);
        }
        // skills
        for (int k = 0; k < 4; k++) {
            int i = p.slotSkill[k];
            int b = B_SK + k;
            if (i < 0) {
                pt.setStyle(Paint.Style.STROKE);
                pt.setStrokeWidth(2f * s);
                pt.setColor(0x40B8C4FF);
                c.drawCircle(bx[b], by[b], br[b], pt);
                continue;
            }
            int col = Data.SKILL_COL[i];
            float cost = p.skillCost(i);
            boolean noKi = p.ki < cost && !(i == 3 && p.transformed);
            boolean cd = p.skCd[i] > 0f;
            circleBtn(c, b, col, bDown[b], noKi || cd ? 0.06f : 0.18f);
            glyphSkill(c, p.cls, i, bx[b], by[b], br[b] * 0.55f, noKi ? Render.a(col, 0.4f) : col);
            if (cd) {
                float frac = Math.min(1f, p.skCd[i] / p.skCdMax(i));
                rf.set(bx[b] - br[b], by[b] - br[b], bx[b] + br[b], by[b] + br[b]);
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(0xAA000000);
                c.drawArc(rf, -90f, 360f * frac, true, pt);
                txt(c, String.valueOf((int) Math.ceil(p.skCd[i])), bx[b], by[b] + 5 * s, 15, 0xFFFFFFFF, Paint.Align.CENTER, true);
            } else {
                txt(c, i == 3 && p.transformed ? "ВИМК" : String.valueOf((int) cost), bx[b], by[b] + br[b] + 12 * s, 10,
                        noKi ? 0xFFFF6A7A : 0xFFB8D8FF, Paint.Align.CENTER, true);
            }
            if (i == 3 && p.transformed) {
                pt.setStyle(Paint.Style.STROKE);
                pt.setStrokeWidth(3 * s);
                pt.setColor(Render.a(Data.GOLD, 0.6f + 0.4f * (float) Math.sin(uiT * 10f)));
                c.drawCircle(bx[b], by[b], br[b] + 4 * s, pt);
            }
        }
        // interact
        if (interactLabel != null) {
            float pu = 1f + 0.06f * (float) Math.sin(uiT * 8f);
            float rr = br[B_INT];
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(Render.a(0xFF4DFFB0, 0.18f));
            c.drawCircle(bx[B_INT], by[B_INT], rr * pu, pt);
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeWidth(3 * s);
            pt.setColor(0xFF4DFFB0);
            c.drawCircle(bx[B_INT], by[B_INT], rr * pu, pt);
            wrapText(c, interactLabel, bx[B_INT], by[B_INT] - 2 * s, rr * 1.6f, 11, 0xFFFFFFFF);
        }
        // pause
        circleBtn(c, B_PAUSE, 0xFFB8C4FF, bDown[B_PAUSE], 0.10f);
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0xFFFFFFFF);
        c.drawRect(bx[B_PAUSE] - 6 * s, by[B_PAUSE] - 7 * s, bx[B_PAUSE] - 2 * s, by[B_PAUSE] + 7 * s, pt);
        c.drawRect(bx[B_PAUSE] + 2 * s, by[B_PAUSE] - 7 * s, bx[B_PAUSE] + 6 * s, by[B_PAUSE] + 7 * s, pt);
    }

    private void ln(Canvas c, float x0, float y0, float x1, float y1, int col, float w) {
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeCap(Paint.Cap.ROUND);
        pt.setStrokeWidth(w);
        pt.setColor(col);
        c.drawLine(x0, y0, x1, y1, pt);
    }

    private void glyphAttack(Canvas c, float x, float y, float r, int col) {
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(Render.a(col, 0.35f));
        c.drawCircle(x, y, r * 0.9f, pt);
        for (int i = 0; i < 8; i++) {
            double a = i * 0.7854 + 0.4;
            float c0 = (float) Math.cos(a), s0 = (float) Math.sin(a);
            ln(c, x + c0 * r * 0.35f, y + s0 * r * 0.35f, x + c0 * r * (i % 2 == 0 ? 1.15f : 0.85f), y + s0 * r * (i % 2 == 0 ? 1.15f : 0.85f),
                    0xFFFFFFFF, 2.5f * S);
        }
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(col);
        c.drawCircle(x, y, r * 0.38f, pt);
    }

    private void glyphSkill(Canvas c, int cls, int slot, float x, float y, float r, int col) {
        float w = 3f * S;
        if (slot == 3) {
            path.reset();
            for (int i = 0; i < 10; i++) {
                double a = -1.5708 + i * 0.6283;
                float rr = (i % 2 == 0) ? r * 1.15f : r * 0.5f;
                float px = x + (float) Math.cos(a) * rr, py = y + (float) Math.sin(a) * rr;
                if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            path.close();
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(col);
            c.drawPath(path, pt);
            return;
        }
        if (slot >= 4) {
            // нові вміння: проста емблема з кількох променів
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(col);
            int rays = slot + 1;
            for (int i = 0; i < rays; i++) {
                double a = i * 6.2832 / rays - 1.5708;
                ln(c, x, y, x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r, col, w);
            }
            c.drawCircle(x, y, r * 0.3f, pt);
            return;
        }
        if (cls == 0) {
            if (slot == 0) {
                ln(c, x - r, y - r * 0.6f, x, y, col, w); ln(c, x, y, x - r, y + r * 0.6f, col, w);
                ln(c, x, y - r * 0.6f, x + r, y, col, w); ln(c, x + r, y, x, y + r * 0.6f, col, w);
            } else if (slot == 1) {
                ln(c, x, y - r, x, y + r * 0.6f, col, w * 1.4f);
                ln(c, x - r * 0.6f, y + r * 0.1f, x, y + r * 0.7f, col, w); ln(c, x + r * 0.6f, y + r * 0.1f, x, y + r * 0.7f, col, w);
                ln(c, x - r, y + r, x + r, y + r, col, w);
            } else {
                for (int i = -1; i <= 1; i++) ln(c, x - r * 0.6f, y + i * r * 0.55f, x + r * 0.9f, y + i * r * 0.55f - i * r * 0.2f, col, w);
            }
        } else {
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(col);
            if (slot == 0) {
                c.drawCircle(x, y, r * 0.8f, pt);
                pt.setColor(0xFFFFFFFF);
                c.drawCircle(x, y, r * 0.35f, pt);
            } else if (slot == 1) {
                ln(c, x - r, y, x + r, y, col, w * 2.2f);
                c.drawCircle(x - r * 0.7f, y, r * 0.5f, pt);
            } else {
                for (int i = -1; i <= 1; i++) c.drawCircle(x + i * r * 0.7f, y - Math.abs(i) * r * 0.25f + r * 0.2f, r * 0.3f, pt);
            }
        }
    }

    // ---------------- level-up ----------------

    private void drawLevelUp(Canvas c) {
        float s = S;
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0xB0060616);
        c.drawRect(0, 0, W, H, pt);
        float a = Math.min(1f, lvlT * 4f);
        txt(c, "РІВЕНЬ " + game.p.level + "!", W / 2f, 56 * s, 36, Render.a(Data.GOLD, a), Paint.Align.CENTER, true);
        txt(c, "Обери покращення", W / 2f, 78 * s, 14, Render.a(0xFFB0B8D8, a), Paint.Align.CENTER, false);
        for (int i = 0; i < 3; i++) {
            float k = Math.max(0f, Math.min(1f, (lvlT - i * 0.08f) * 5f));
            if (k <= 0f) continue;
            float ease = 1f - (1f - k) * (1f - k);
            int id = game.cards[i];
            int col = Data.cardColor(id);
            float cx = W / 2f + (i - 1) * 218 * s;
            float cy = 205 * s + (1f - ease) * 40 * s;
            c.save();
            c.scale(0.8f + 0.2f * ease, 0.8f + 0.2f * ease, cx, cy);
            panel(c, cx, cy, 200 * s, 250 * s, col, 0.92f);
            // icon
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(Render.a(col, 0.2f));
            c.drawCircle(cx, cy - 62 * s, 38 * s, pt);
            pt.setColor(Render.a(col, 0.5f));
            c.drawCircle(cx, cy - 62 * s, 26 * s, pt);
            boolean skill = id >= Data.C_UNL0 && id <= Data.C_UPGT;
            if (skill) glyphSkill(c, game.p.cls, (id - Data.C_UNL0) % 4, cx, cy - 62 * s, 20 * s, 0xFFFFFFFF);
            else {
                txt(c, id <= Data.C_LUK ? "+2" : "+", cx, cy - 52 * s, 28, 0xFFFFFFFF, Paint.Align.CENTER, true);
            }
            txt(c, Data.cardTitle(id, game.p.cls), cx, cy + 8 * s, 15, col, Paint.Align.CENTER, true);
            wrapText(c, Data.cardDesc(id, game.p.cls), cx, cy + 32 * s, 170 * s, 11, 0xFFE0E4F8);
            if (id >= Data.C_UNL0 && id <= Data.C_UNLT) txt(c, "НОВЕ", cx, cy - 98 * s, 11, 0xFFFF4DDB, Paint.Align.CENTER, true);
            c.restore();
        }
    }

    // ---------------- pause / dead ----------------

    // ======================= ПАУЗА / МЕНЮ =======================
    private int pauseTab = 0;
    private int selKind = 0, selA = -1, selB = -1;   // 1 предмет, 2 камінь сумки, 3 слот вміння, 4 гніздо, 5 пасив, 6 камінь у списку
    private boolean pickerOpen;
    private int pickSk = -1, pickJ = -1;
    private int pdKind, pdA, pdB, pdPid = -1;
    private boolean dragging;
    private float pdX, pdY, dgX, dgY;
    private int cfKind, cfA, cfB;
    private String cfText = "";
    private int hitK, hitA, hitB;
    private static final int H_TAB = 11, H_CONT = 30, H_MENU = 31, H_ACT1 = 32, H_ACT2 = 33, H_PCLOSE = 34, H_YES = 35, H_NO = 36;

    private static final String[] TABS = {"ПЕРСОНАЖ", "ІНВЕНТАР", "УМІННЯ"};

    private float wv() { return W / S; }
    private float lw() { return wv() - 210f; }
    private float cardW() { return (lw() - 24f) / 4f; }
    private float cardX(int k) { return 14f + k * (cardW() + 8f) + cardW() / 2f; }
    private float sockCell() { return Math.min(30f, (cardW() - 12f) / 4f); }
    private float sockX(int k, int j) { return cardX(k) + ((j % 4) - 1.5f) * sockCell(); }
    private float sockY(int j) { return 40f + 118f + (j / 4) * sockCell() + sockCell() / 2f; }
    private float passX(int q) { float pw = (lw() - 8f) / 2f; return 14f + q * (pw + 8f) + pw / 2f; }
    private int gridCols() { return Math.max(6, (int) ((lw() - 8f) / 40f)); }
    private float itX(int i) { return 14f + (i % gridCols()) * 40f + 18f; }
    private float itY(int i) { return 60f + (i / gridCols()) * 40f + 18f; }
    private float igX(int i) { return itX(i); }
    private float igY(int i) { return 136f + (i / gridCols()) * 40f + 18f; }
    private int pkCols() { return Math.max(4, (int) ((lw() - 28f) / 42f)); }
    private float pkX(int i) { return 28f + (i % pkCols()) * 42f + 20f; }
    private float pkY(int i) { return 40f + 52f + (i / pkCols()) * 42f + 20f; }
    private float ipx() { return wv() - 182f; }

    private boolean inV(float vx, float vy, float cx, float cy, float w, float h) {
        return vx > cx - w / 2 && vx < cx + w / 2 && vy > cy - h / 2 && vy < cy + h / 2;
    }

    private int[] ownedItems() {
        Player p = game.p;
        int n = 0;
        int[] r = new int[Data.ITEMS];
        for (int i = 0; i < Data.ITEMS; i++) if (p.items[i] > 0) r[n++] = i;
        int[] o = new int[n];
        System.arraycopy(r, 0, o, 0, n);
        return o;
    }

    private void clearSel() { selKind = 0; selA = selB = -1; pickerOpen = false; pickSk = pickJ = -1; cfKind = 0; dragging = false; pdKind = 0; pdPid = -1; }

    /** що під пальцем: заповнює hitK/hitA/hitB */
    private boolean pausePick(float x, float y) {
        float vx = x / S, vy = y / S;
        Player p = game.p;
        hitK = 0; hitA = hitB = -1;
        if (cfKind != 0) {
            if (inV(vx, vy, wv() / 2f - 70f, 226f, 120f, 30f)) { hitK = H_YES; return true; }
            if (inV(vx, vy, wv() / 2f + 70f, 226f, 120f, 30f)) { hitK = H_NO; return true; }
            return false;
        }
        for (int t = 0; t < 3; t++) if (inV(vx, vy, wv() / 2f + (t - 1) * 120f, 16f, 112f, 22f)) { hitK = H_TAB; hitA = t; return true; }
        if (inV(vx, vy, wv() / 2f - 100f, 372f, 170f, 26f)) { hitK = H_CONT; return true; }
        if (inV(vx, vy, wv() / 2f + 100f, 372f, 170f, 26f)) { hitK = H_MENU; return true; }
        float ix = ipx() + 84f;
        if (hasAct(1) && inV(vx, vy, ix, 326f, 148f, 26f)) { hitK = H_ACT1; return true; }
        if (hasAct(2) && inV(vx, vy, ix, 294f, 148f, 26f)) { hitK = H_ACT2; return true; }
        if (pauseTab == 1) {
            int[] own = ownedItems();
            for (int i = 0; i < own.length; i++) if (inV(vx, vy, itX(i), itY(i), 36f, 36f)) { hitK = 1; hitA = own[i]; return true; }
            for (int i = 0; i < p.bag.size() && i < gridCols() * 5; i++) if (inV(vx, vy, igX(i), igY(i), 36f, 36f)) { hitK = 2; hitA = i; return true; }
        } else if (pauseTab == 2) {
            if (pickerOpen) {
                if (inV(vx, vy, 14f + lw() - 56f, 40f + 20f, 96f, 24f)) { hitK = H_PCLOSE; return true; }
                for (int i = 0; i < p.bag.size() && i < pkCols() * 4; i++) if (inV(vx, vy, pkX(i), pkY(i), 38f, 38f)) { hitK = 6; hitA = i; return true; }
                return false;
            }
            for (int k = 0; k < 4; k++) {
                int sk = p.slotSkill[k];
                if (sk < 0) continue;
                for (int j = 0; j < p.sockN[sk]; j++) if (inV(vx, vy, sockX(k, j), sockY(j), sockCell(), sockCell())) { hitK = 4; hitA = sk; hitB = j; return true; }
            }
            for (int k = 0; k < 4; k++) if (inV(vx, vy, cardX(k), 40f + 90f, cardW(), 180f)) { hitK = 3; hitA = k; return true; }
            for (int q = 0; q < 2; q++) {
                int ps = p.passSlot[q];
                if (ps >= 0 && inV(vx, vy, passX(q), 260f, (lw() - 8f) / 2f, 56f)) { hitK = 5; hitA = ps; return true; }
            }
        }
        return false;
    }

    private boolean hasAct(int n) {
        Player p = game.p;
        if (cfKind != 0) return false;
        if (pauseTab == 2 && selKind == 4 && selA >= 0 && selB >= 0 && p.sock[selA][selB] != null) return n == 1;
        if (pauseTab == 2 && selKind == 6 && selA >= 0 && selA < p.bag.size() && pickerOpen) return n == 1;
        return false;
    }

    private void pauseDown(int id, float x, float y) {
        if (pdPid >= 0) return;
        pausePick(x, y);
        pdKind = hitK; pdA = hitA; pdB = hitB; pdPid = id;
        pdX = x; pdY = y; dgX = x; dgY = y; dragging = false;
    }

    private void pauseMove(int id, float x, float y) {
        if (id != pdPid) return;
        dgX = x; dgY = y;
        if (!dragging && (pdKind == 3 || pdKind == 2)) {
            float dx = x - pdX, dy = y - pdY;
            if (dx * dx + dy * dy > 100f * S * S) dragging = true;
        }
    }

    private void pauseUp(int id) {
        if (id != pdPid) return;
        pdPid = -1;
        Player p = game.p;
        int k0 = pdKind, a0 = pdA, b0 = pdB;
        boolean dr = dragging;
        dragging = false;
        pdKind = 0;
        if (cfKind != 0) {
            pausePick(dgX, dgY);
            if (hitK == H_YES) { doConfirm(); }
            else if (hitK == H_NO) { cfKind = 0; audio.play(Audio.CLICK, 0.5f); }
            return;
        }
        if (dr) {
            pausePick(dgX, dgY);
            if (k0 == 3 && hitK == 3 && hitA != a0) {
                int t = p.slotSkill[a0]; p.slotSkill[a0] = p.slotSkill[hitA]; p.slotSkill[hitA] = t;
                selKind = 3; selA = hitA;
                audio.play(Audio.CLICK, 0.7f);
            } else if (k0 == 2 && hitK == 2 && hitA != a0 && a0 < p.bag.size() && hitA < p.bag.size()
                    && p.bag.get(a0).type == p.bag.get(hitA).type) {
                cfKind = 1; cfA = a0; cfB = hitA;
                Ent.Gem g1 = p.bag.get(a0), g2 = p.bag.get(hitA);
                cfText = "Об'єднати два камені «" + Data.GEM_NAME[g1.type] + "» у один рівня " + Math.min(10, Math.max(g1.lvl, g2.lvl) + 1) + "?";
            }
            return;
        }
        // дотик
        int hk = k0, ha = a0, hb = b0;
        audio.play(Audio.CLICK, 0.5f);
        switch (hk) {
            case H_TAB: pauseTab = ha; clearSel(); break;
            case H_CONT: clearSel(); screen = PLAY; audio.setDuck(false); break;
            case H_MENU: clearSel(); toTitle(); break;
            case 1: selKind = 1; selA = ha; break;
            case 2: selKind = 2; selA = ha; break;
            case 3: selKind = 3; selA = ha; break;
            case 5: selKind = 5; selA = ha; break;
            case 4:
                selKind = 4; selA = ha; selB = hb;
                if (p.sock[ha][hb] == null) {
                    pickerOpen = true; pickSk = ha; pickJ = hb; selKind = 4;
                }
                break;
            case 6: selKind = 6; selA = ha; break;
            case H_PCLOSE: pickerOpen = false; selKind = 0; break;
            case H_ACT1:
                if (selKind == 4 && p.sock[selA][selB] != null) {
                    p.bag.add(p.sock[selA][selB]); p.sock[selA][selB] = null;
                    selKind = 0; audio.play(Audio.PICKUP, 0.6f);
                } else if (selKind == 6 && selA >= 0 && selA < p.bag.size() && pickSk >= 0) {
                    Ent.Gem gg = p.bag.get(selA);
                    cfKind = 2; cfA = selA; cfB = pickJ;
                    cfText = "Вставити «" + Data.GEM_NAME[gg.type] + "» (рів. " + gg.lvl + ") у вміння «" + Data.SKILL_NAME[p.cls][pickSk] + "»?";
                }
                break;
            default: break;
        }
    }

    private void doConfirm() {
        Player p = game.p;
        if (cfKind == 1 && cfA < p.bag.size() && cfB < p.bag.size()) {
            Ent.Gem a = p.bag.get(cfA), b = p.bag.get(cfB);
            b.lvl = Math.min(10, Math.max(a.lvl, b.lvl) + 1);
            p.bag.remove(cfA);
            selKind = 2; selA = p.bag.indexOf(b);
            audio.play(Audio.PICKUP, 0.8f);
        } else if (cfKind == 2 && cfA < p.bag.size() && pickSk >= 0) {
            Ent.Gem g2 = p.bag.get(cfA);
            Ent.Gem same = null;
            for (int q = 0; q < p.sockN[pickSk]; q++) if (p.sock[pickSk][q] != null && p.sock[pickSk][q].type == g2.type) same = p.sock[pickSk][q];
            if (same != null) same.lvl = Math.min(10, Math.max(same.lvl, g2.lvl) + 1);
            else if (p.sock[pickSk][pickJ] == null) p.sock[pickSk][pickJ] = g2;
            else { cfKind = 0; return; }
            p.bag.remove(cfA);
            pickerOpen = false; selKind = 4; selA = pickSk; selB = pickJ;
            audio.play(Audio.PICKUP, 0.8f);
        }
        cfKind = 0;
    }

    private void gemShape(Canvas c, float x, float y, float r, int col) {
        path.reset();
        path.moveTo(x, y - r); path.lineTo(x + r * 0.8f, y); path.lineTo(x, y + r); path.lineTo(x - r * 0.8f, y); path.close();
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(Render.a(col, 0.35f));
        c.drawPath(path, pt);
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(2f * S);
        pt.setColor(col);
        c.drawPath(path, pt);
    }

    private void cell(Canvas c, float cx, float cy, float sz, int col, boolean sel, boolean glow) {
        float s = S;
        rf.set((cx - sz / 2) * s, (cy - sz / 2) * s, (cx + sz / 2) * s, (cy + sz / 2) * s);
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(Render.a(0xFF141A38, 0.95f));
        c.drawRoundRect(rf, 6 * s, 6 * s, pt);
        if (glow) { pt.setColor(Render.a(col, 0.16f)); c.drawRoundRect(rf, 6 * s, 6 * s, pt); }
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth((sel ? 2.4f : 1.4f) * s);
        pt.setColor(sel ? 0xFFFFFFFF : Render.a(col, glow ? 0.7f : 0.25f));
        c.drawRoundRect(rf, 6 * s, 6 * s, pt);
    }

    private void button(Canvas c, float cx, float cy, float w, float h, String label, int col, boolean on) {
        float s = S;
        panel(c, cx * s, cy * s, w * s, h * s, col, on ? 0.95f : 0.6f);
        txt(c, label, cx * s, (cy + 4.5f) * s, 12, on ? col : 0xFF8A93B8, Paint.Align.CENTER, true);
    }

    private float wrapL(Canvas c, String t, float x, float y, float maxW, float size, int col) {
        float s = S;
        pt.setTextSize(size * s);
        pt.setFakeBoldText(false);
        String[] words = t.split(" ");
        StringBuilder line = new StringBuilder();
        float yy = y;
        for (String w : words) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (pt.measureText(test) > maxW * s && line.length() > 0) {
                txt(c, line.toString(), x * s, yy * s, size, col, Paint.Align.LEFT, false);
                yy += size * 1.28f;
                line.setLength(0);
                line.append(w);
            } else { line.setLength(0); line.append(test); }
        }
        if (line.length() > 0) { txt(c, line.toString(), x * s, yy * s, size, col, Paint.Align.LEFT, false); yy += size * 1.28f; }
        return yy;
    }

    private void drawPause(Canvas c) {
        float s = S;
        Player p = game.p;
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0xF0050614);
        c.drawRect(0, 0, W, H, pt);
        for (int t = 0; t < 3; t++) {
            float cx = wv() / 2f + (t - 1) * 120f;
            boolean on = pauseTab == t;
            panel(c, cx * s, 16 * s, 112 * s, 22 * s, on ? 0xFFFFD84A : 0xFF4A5278, on ? 0.95f : 0.55f);
            txt(c, TABS[t], cx * s, 20.5f * s, 11, on ? 0xFFFFD84A : 0xFF8A93B8, Paint.Align.CENTER, true);
        }
        if (pauseTab == 0) {
            c.save();
            c.translate(W / 2f, 200 * s);
            c.scale(0.8f, 0.8f);
            c.translate(-W / 2f, -190 * s);
            panel(c, W / 2f, 190 * s, wv() * s - 40 * s, 290 * s, 0xFF35D6FF, 0.92f);
            drawTabChar(c, p, 32 * s);
            c.restore();
        } else {
            if (pauseTab == 1) drawTabInv(c, p); else drawTabSkills(c, p);
            drawInfo(c, p);
        }
        button(c, wv() / 2f - 100f, 372f, 170f, 26f, "ПРОДОВЖИТИ", 0xFF4DFFB0, true);
        button(c, wv() / 2f + 100f, 372f, 170f, 26f, "В МЕНЮ", 0xFFFF4D6D, true);
        // перетягуваний об'єкт
        if (dragging && cfKind == 0) {
            c.saveLayerAlpha(0, 0, W, H, 150);
            if (pdKind == 3) {
                int sk = p.slotSkill[pdA];
                if (sk >= 0) {
                    int col = Data.SKILL_COL[sk];
                    panel(c, dgX, dgY, cardW() * s * 0.6f, 120 * s, col, 0.9f);
                    glyphSkill(c, p.cls, sk, dgX, dgY - 14 * s, 15 * s, col);
                    txt(c, Data.SKILL_NAME[p.cls][sk], dgX, dgY + 34 * s, 11, col, Paint.Align.CENTER, true);
                }
            } else if (pdKind == 2 && pdA < p.bag.size()) {
                gemShape(c, dgX, dgY, 16 * s, Data.GEM_COL[p.bag.get(pdA).type]);
            }
            c.restore();
        }
        if (cfKind != 0) {
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(0xB0000008);
            c.drawRect(0, 0, W, H, pt);
            panel(c, W / 2f, 190 * s, 330 * s, 130 * s, 0xFFFFD84A, 0.97f);
            txt(c, "ПІДТВЕРДИТИ", W / 2f, 152 * s, 12, 0xFFFFD84A, Paint.Align.CENTER, true);
            wrapText(c, cfText, W / 2f, 176 * s, 290 * s, 11, 0xFFE8ECFF);
            button(c, wv() / 2f - 70f, 226f, 120f, 30f, "ТАК", 0xFF4DFFB0, true);
            button(c, wv() / 2f + 70f, 226f, 120f, 30f, "НІ", 0xFFFF4D6D, true);
        }
    }

    private void drawTabInv(Canvas c, Player p) {
        float s = S;
        int[] own = ownedItems();
        txt(c, "ПРЕДМЕТИ", 14 * s, 52 * s, 11, 0xFFFFD84A, Paint.Align.LEFT, true);
        int rowsI = Math.max(1, (Data.ITEMS + gridCols() - 1) / gridCols());
        for (int i = 0; i < gridCols() * rowsI && i < Data.ITEMS; i++) {
            boolean has = i < own.length;
            int it = has ? own[i] : -1;
            int col = has ? Data.ITEM_COL[it] : 0xFF607098;
            cell(c, itX(i), itY(i), 36f, col, has && selKind == 1 && selA == it, has);
            if (has) {
                txt(c, Data.ITEM_NAME[it].substring(0, 1), itX(i) * s, (itY(i) + 6f) * s, 17, col, Paint.Align.CENTER, true);
                if (p.items[it] > 1) txt(c, "x" + p.items[it], (itX(i) + 16f) * s, (itY(i) + 15f) * s, 8, 0xFFFFFFFF, Paint.Align.RIGHT, true);
            }
        }
        txt(c, "КАМЕНІ (" + p.bag.size() + ")", 14 * s, 128 * s, 11, 0xFF6A7BFF, Paint.Align.LEFT, true);
        int slots = Math.max(gridCols() * 2, Math.min(gridCols() * 5, ((p.bag.size() / gridCols()) + 1) * gridCols()));
        for (int i = 0; i < slots; i++) {
            boolean has = i < p.bag.size();
            boolean dragged = dragging && pdKind == 2 && pdA == i;
            boolean target = dragging && pdKind == 2 && has && i != pdA && p.bag.get(i).type == p.bag.get(pdA).type
                    && inV(dgX / s, dgY / s, igX(i), igY(i), 36f, 36f);
            int col = has ? Data.GEM_COL[p.bag.get(i).type] : 0xFF607098;
            cell(c, igX(i), igY(i), 36f, col, (selKind == 2 && selA == i) || target, has);
            if (has && !dragged) {
                gemShape(c, igX(i) * s, (igY(i) - 1f) * s, 12 * s, col);
                txt(c, String.valueOf(p.bag.get(i).lvl), (igX(i) + 16f) * s, (igY(i) + 15f) * s, 9, 0xFFFFFFFF, Paint.Align.RIGHT, true);
            }
        }
        if (!p.bag.isEmpty()) txt(c, "Перетягни камінь на такий самий, щоб об'єднати", 14 * s, 352 * s, 9, 0xFF6A7298, Paint.Align.LEFT, false);
    }

    private void drawTabSkills(Canvas c, Player p) {
        float s = S;
        int tgt = -1;
        if (dragging && pdKind == 3) for (int k = 0; k < 4; k++) if (inV(dgX / s, dgY / s, cardX(k), 130f, cardW(), 180f)) tgt = k;
        for (int k = 0; k < 4; k++) {
            int sk = p.slotSkill[k];
            int col = sk >= 0 ? Data.SKILL_COL[sk] : 0xFF3A3F5C;
            boolean sel = (selKind == 3 && selA == k) || tgt == k;
            float cx = cardX(k);
            panel(c, cx * s, 130 * s, cardW() * s, 180 * s, sel ? 0xFFFFFFFF : col, 0.8f);
            txt(c, String.valueOf(k + 1), (cx - cardW() / 2f + 12f) * s, 58 * s, 10, 0xFF8A93B8, Paint.Align.LEFT, true);
            if (sk < 0) { txt(c, "порожньо", cx * s, 134 * s, 11, 0xFF5A6080, Paint.Align.CENTER, false); continue; }
            boolean ghost = dragging && pdKind == 3 && pdA == k;
            if (!ghost) {
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(Render.a(col, 0.18f));
                c.drawCircle(cx * s, 82 * s, 20 * s, pt);
                glyphSkill(c, p.cls, sk, cx * s, 82 * s, 12 * s, col);
                txt(c, Data.SKILL_NAME[p.cls][sk], cx * s, 124 * s, 11, col, Paint.Align.CENTER, true);
                txt(c, "Рівень " + (p.skLvl[sk] + 1), cx * s, 138 * s, 9, 0xFFFFD84A, Paint.Align.CENTER, true);
            }
            int ns = p.sockN[sk];
            for (int j = 0; j < ns; j++) {
                float sx = sockX(k, j), sy = sockY(j);
                Ent.Gem gm = p.sock[sk][j];
                boolean ss = selKind == 4 && selA == sk && selB == j;
                float cs = sockCell() - 3f;
                if (gm != null) {
                    cell(c, sx, sy, cs, Data.GEM_COL[gm.type], ss, true);
                    gemShape(c, sx * s, sy * s, cs * 0.34f * s, Data.GEM_COL[gm.type]);
                } else {
                    cell(c, sx, sy, cs, 0xFF8890B0, ss, false);
                    txt(c, "+", sx * s, (sy + 4f) * s, 12, 0xFF6A7298, Paint.Align.CENTER, true);
                }
            }
        }
        for (int q = 0; q < 2; q++) {
            int ps = p.passSlot[q];
            float pw = (lw() - 8f) / 2f, cx = passX(q);
            boolean sel = selKind == 5 && selA == ps && ps >= 0;
            panel(c, cx * s, 260 * s, pw * s, 56 * s, ps >= 0 ? (sel ? 0xFFFFFFFF : Data.PAS_COL[ps]) : 0xFF3A3F5C, 0.8f);
            if (ps < 0) { txt(c, "пасивне вміння: порожньо", cx * s, 264 * s, 10, 0xFF5A6080, Paint.Align.CENTER, false); continue; }
            int col = Data.PAS_COL[ps];
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(Render.a(col, 0.25f));
            c.drawCircle((cx - pw / 2f + 28f) * s, 260 * s, 16 * s, pt);
            txt(c, Data.PAS_NAME[ps].substring(0, 1), (cx - pw / 2f + 28f) * s, 266 * s, 16, col, Paint.Align.CENTER, true);
            txt(c, Data.PAS_NAME[ps], (cx - pw / 2f + 54f) * s, 256 * s, 11, col, Paint.Align.LEFT, true);
            txt(c, "Рівень " + p.passLvl[ps] + " / 5", (cx - pw / 2f + 54f) * s, 271 * s, 9, 0xFFFFD84A, Paint.Align.LEFT, true);
        }
        txt(c, "Перетягни вміння на інше, щоб поміняти місцями. Торкнись гнізда, щоб вставити камінь.", 14 * s, 352 * s, 9, 0xFF6A7298, Paint.Align.LEFT, false);
        if (pickerOpen) {
            panel(c, (14f + lw() / 2f) * s, 164 * s, lw() * s, 250 * s, 0xFF6A7BFF, 0.98f);
            txt(c, "ОБЕРИ КАМІНЬ", 28 * s, 62 * s, 11, 0xFFFFD84A, Paint.Align.LEFT, true);
            button(c, 14f + lw() - 56f, 60f, 96f, 24f, "ЗАКРИТИ", 0xFFFF4D6D, true);
            if (p.bag.isEmpty()) txt(c, "Сумка порожня. Камені випадають з босів і скринь.", 28 * s, 110 * s, 10, 0xFF8A93B8, Paint.Align.LEFT, false);
            for (int i = 0; i < p.bag.size() && i < pkCols() * 4; i++) {
                int col = Data.GEM_COL[p.bag.get(i).type];
                cell(c, pkX(i), pkY(i), 38f, col, selKind == 6 && selA == i, true);
                gemShape(c, pkX(i) * s, (pkY(i) - 1f) * s, 12 * s, col);
                txt(c, String.valueOf(p.bag.get(i).lvl), (pkX(i) + 17f) * s, (pkY(i) + 16f) * s, 9, 0xFFFFFFFF, Paint.Align.RIGHT, true);
            }
        }
    }

    private void drawInfo(Canvas c, Player p) {
        float s = S, px = ipx(), pw = 168f;
        int col = 0xFF4A5278;
        String name = null, sub = null, desc = null, extra = null;
        float gx = 0;
        if (selKind == 1 && p.items[selA] > 0) {
            col = Data.ITEM_COL[selA]; name = Data.ITEM_NAME[selA]; sub = "Кількість: " + p.items[selA];
            desc = Data.ITEM_DESC[selA] + (p.items[selA] > 1 ? " (за кожну копію)" : "");
        } else if ((selKind == 2 || selKind == 6) && selA >= 0 && selA < p.bag.size()) {
            Ent.Gem g = p.bag.get(selA);
            col = Data.GEM_COL[g.type]; name = Data.GEM_NAME[g.type]; sub = "Камінь, рівень " + g.lvl; desc = Data.GEM_DESC[g.type];
            gx = 1;
        } else if (selKind == 3 && selA >= 0 && p.slotSkill[selA] >= 0) {
            int sk = p.slotSkill[selA];
            col = Data.SKILL_COL[sk]; name = Data.SKILL_NAME[p.cls][sk];
            sub = "Рівень " + (p.skLvl[sk] + 1) + " / 5" + (p.skBr[sk] == 1 ? " · Міць" : (p.skBr[sk] == 2 ? " · Швидкість" : ""));
            desc = Data.SKILL_DESC[p.cls][sk];
            extra = "Енергія " + (int) p.skillCost(sk) + " · Перезарядка " + String.format("%.1f", p.skCdMax(sk)) + " с · Гнізда " + p.sockN[sk];
        } else if (selKind == 4 && selA >= 0) {
            Ent.Gem g = p.sock[selA][selB];
            if (g != null) { col = Data.GEM_COL[g.type]; name = Data.GEM_NAME[g.type]; sub = "У гнізді, рівень " + g.lvl; desc = Data.GEM_DESC[g.type]; gx = 1; }
            else { name = "Порожнє гніздо"; sub = Data.SKILL_NAME[p.cls][selA]; desc = "Обери камінь у списку й вставити його."; }
        } else if (selKind == 5 && selA >= 0 && p.passLvl[selA] > 0) {
            col = Data.PAS_COL[selA]; name = Data.PAS_NAME[selA]; sub = "Пасивне, рівень " + p.passLvl[selA] + " / 5"; desc = Data.PAS_DESC[selA];
        }
        panel(c, (px + pw / 2f) * s, 190 * s, pw * s, 306 * s, col, 0.85f);
        if (name == null) {
            wrapL(c, pauseTab == 1 ? "Торкнись предмета чи каменя, щоб побачити опис." : "Торкнись вміння, гнізда чи пасивного, щоб побачити опис.", px + 12f, 72f, pw - 24f, 10, 0xFF8A93B8);
            return;
        }
        float y = 70f;
        if (gx > 0) { gemShape(c, (px + 24f) * s, (y + 2f) * s, 14 * s, col); }
        else if (selKind == 1) {
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(Render.a(col, 0.3f));
            rf.set((px + 10f) * s, (y - 12f) * s, (px + 38f) * s, (y + 16f) * s);
            c.drawRoundRect(rf, 6 * s, 6 * s, pt);
            txt(c, name.substring(0, 1), (px + 24f) * s, (y + 9f) * s, 17, col, Paint.Align.CENTER, true);
        }
        txt(c, name, (px + 46f) * s, (y - 2f) * s, 12, col, Paint.Align.LEFT, true);
        txt(c, sub, (px + 46f) * s, (y + 12f) * s, 9, 0xFFFFD84A, Paint.Align.LEFT, true);
        y = wrapL(c, desc, px + 12f, y + 38f, pw - 24f, 10, 0xFFD0D8FF);
        if (extra != null) wrapL(c, extra, px + 12f, y + 6f, pw - 24f, 9, 0xFF8FD8FF);
        if (hasAct(1)) button(c, px + 84f, 326f, 148f, 26f, selKind == 6 ? "ВСТАВИТИ" : "ВИЙНЯТИ", selKind == 6 ? 0xFF4DFFB0 : 0xFFFFB020, true);
    }

    private void statRow(Canvas c, float x, float y, float w, float h, int idx, String name, String val, int col) {
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(Render.a(idx % 2 == 0 ? 0xFF1A2040 : 0xFF121830, 0.85f));
        rf.set(x, y, x + w, y + h);
        c.drawRoundRect(rf, 3 * S, 3 * S, pt);
        txt(c, name, x + 8 * S, y + h * 0.72f, 11, 0xFFA8B4DC, Paint.Align.LEFT, false);
        txt(c, val, x + w - 8 * S, y + h * 0.72f, 12, col, Paint.Align.RIGHT, true);
    }

    private void drawTabChar(Canvas c, Player p, float L) {
        float s = S, Wv = wv();
        // портрет
        panel(c, L + 100 * s, 190 * s, 190 * s, 262 * s, Data.CLS_COL[p.cls], 0.5f);
        txt(c, Data.CLS_NAME[p.cls], L + 100 * s, 70 * s, 18, Data.CLS_COL[p.cls], Paint.Align.CENTER, true);
        txt(c, "РІВЕНЬ " + p.level, L + 100 * s, 86 * s, 12, 0xFFFFD84A, Paint.Align.CENTER, true);
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(Render.a(Data.CLS_COL[p.cls], 0.10f));
        c.drawCircle(L + 100 * s, 190 * s, 70 * s, pt);
        c.save();
        c.translate(L + 100 * s, 250 * s);
        c.scale(2.4f * s, 2.4f * s);
        Rig.draw(c, pt, 0f, 0f, 1, Rig.IDLE, p.color(), 1f, true, p.hairLen(), p.hairColor());
        c.restore();
        barG(2, c, L + 20 * s, 276 * s, 160 * s, 8 * s, p.xp / p.xpNeed(), 0xFFFFD84A);
        txt(c, "ДОСВІД " + (int) p.xp + " / " + (int) p.xpNeed(), L + 100 * s, 298 * s, 10, 0xFFFFD84A, Paint.Align.CENTER, false);
        txt(c, "ЛОКАЦІЯ " + game.stage + " · " + clock(game.runTime) + " · ВБИТО " + game.totalKills, L + 100 * s, 316 * s, 9, 0xFF8A93B8, Paint.Align.CENTER, false);
        // основні
        float x1 = L + 214 * s;
        txt(c, "ХАРАКТЕРИСТИКИ", x1, 70 * s, 12, 0xFFFFD84A, Paint.Align.LEFT, true);
        String[] nm = {"СИЛА", "ЖИВУЧІСТЬ", "СПРИТНІСТЬ", "ДУХ", "УДАЧА"};
        String[] sub = {"шкода атак", "HP і захист", "швидкість, ривки", "енергія", "крити, дроп"};
        float[] vv = {p.str, p.vit, p.agi, p.spi, p.effLuk()};
        int[] cc = {0xFFFF7A2E, 0xFF4DFF88, 0xFF35D6FF, 0xFF6A7BFF, 0xFFFFD84A};
        for (int i = 0; i < 5; i++) {
            float y = (80 + i * 46) * s;
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(Render.a(0xFF141A38, 0.9f));
            rf.set(x1, y, x1 + 210 * s, y + 40 * s);
            c.drawRoundRect(rf, 4 * s, 4 * s, pt);
            pt.setColor(Render.a(cc[i], 0.9f));
            rf.set(x1 + 5 * s, y + 5 * s, x1 + 35 * s, y + 35 * s);
            c.drawRoundRect(rf, 4 * s, 4 * s, pt);
            txt(c, nm[i].substring(0, 1), x1 + 20 * s, y + 27 * s, 16, 0xFF0A0A1C, Paint.Align.CENTER, true);
            txt(c, nm[i], x1 + 44 * s, y + 17 * s, 11, 0xFFFFFFFF, Paint.Align.LEFT, true);
            txt(c, sub[i], x1 + 44 * s, y + 31 * s, 9, 0xFF8A93B8, Paint.Align.LEFT, false);
            txt(c, String.valueOf((int) vv[i]), x1 + 200 * s, y + 27 * s, 18, cc[i], Paint.Align.RIGHT, true);
        }
        // похідні
        float x2 = x1 + 232 * s, w2 = Wv * s - 52 * s - x2;
        txt(c, "ПОКАЗНИКИ", x2, 70 * s, 12, 0xFFFFD84A, Paint.Align.LEFT, true);
        String[][] rows = {
            {"Здоров'я", (int) p.hp + " / " + (int) p.maxHp()},
            {"Енергія", (int) p.ki + " / " + (int) p.maxKi()},
            {"Регенерація HP", String.format("%.1f/с", p.hpRegen())},
            {"Регенерація ki", String.format("%.1f/с", p.kiRegen())},
            {"Шкода", "+" + (int) ((p.dmgMul() - 1f) * 100f) + "%"},
            {"Шанс крита", (int) (p.critChance() * 100f) + "%"},
            {"Шкода крита", "x" + String.format("%.2f", p.critMul())},
            {"Захист", String.valueOf((int) p.defense())},
            {"Швидкість руху", (int) p.moveSpeed() + ""},
            {"Швидкість атак", "x" + String.format("%.2f", p.aspd())},
            {"Ривок", p.dashMax + " заряди, " + String.format("%.1f", p.dashCd()) + " с"},
            {"Удача дропу", "x" + String.format("%.2f", p.dropMul())}};
        for (int i = 0; i < rows.length; i++)
            statRow(c, x2, (80 + i * 20.5f) * s, w2, 19 * s, i, rows[i][0], rows[i][1], 0xFFFFFFFF);
    }

    private void drawDead(Canvas c) {
        float s = S;
        Player p = game.p;
        float a = Math.min(1f, deadT * 1.5f);
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(Render.a(0xFF060616, 0.8f * a));
        c.drawRect(0, 0, W, H, pt);
        txt(c, "ТИ ПАВ", W / 2f, 80 * s, 50, Render.a(0xFFFF4D6D, a), Paint.Align.CENTER, true);
        String[] l = {"Локація: " + game.stage, "Вбито ворогів: " + game.totalKills, "Босів переможено: " + game.bossesKilled,
                "Рівень: " + p.level, "Час: " + clock(game.runTime)};
        for (int i = 0; i < l.length; i++) {
            txt(c, l[i], W / 2f, (130 + i * 24) * s, 16, Render.a(0xFFFFFFFF, a), Paint.Align.CENTER, false);
        }
        if (deadT > 0.8f) {
            panel(c, W / 2f - 130 * s, 330 * s, 220 * s, 46 * s, 0xFF4DFFB0, 0.9f);
            txt(c, "ЩЕ РАЗ", W / 2f - 130 * s, 337 * s, 18, 0xFF4DFFB0, Paint.Align.CENTER, true);
            panel(c, W / 2f + 130 * s, 330 * s, 220 * s, 46 * s, 0xFFB8C4FF, 0.9f);
            txt(c, "В МЕНЮ", W / 2f + 130 * s, 337 * s, 18, 0xFFB8C4FF, Paint.Align.CENTER, true);
        }
    }
}
