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
    private static final int TITLE = 0, PLAY = 1, PAUSE = 2, DEAD = 3, INTRO = 4;
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

    private int screen = TITLE;
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
        audio.setMusic(screen == TITLE ? Audio.MUS_TITLE : (game.boss != null ? Audio.MUS_BOSS : Audio.MUS_STAGE));
        lastNanos = 0;
    }

    public void onPauseGame() {
        if (screen == PLAY && !game.levelUpPending) {
            screen = PAUSE;
            audio.setDuck(true);
        }
        resetInput();
        audio.setCharging(false, 0f);
        audio.stop();
    }

    public boolean onBack() {
        if (screen == PLAY && !game.levelUpPending) { screen = PAUSE; audio.setDuck(true); resetInput(); return true; }
        if (screen == PAUSE) { screen = PLAY; audio.setDuck(false); return true; }
        if (screen == DEAD) { toTitle(); return true; }
        return false;
    }

    private void toTitle() {
        screen = TITLE;
        audio.setDuck(false);
        audio.setMusic(Audio.MUS_TITLE);
        game.newRun(selCls, selDiff);
        resetInput();
    }

    private void startRun() {
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
        c.left = c.right = c.chargeHeld = c.crouchHeld = c.atkHeld = false;
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
            if (i >= B_SK && i < B_SK + 4 && !game.p.unlocked[i - B_SK]) continue;
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
            c.skillReq[b - B_SK] = true;
        } else if (b == B_INT) {
            c.interactReq = true;
        } else if (b == B_PAUSE) {
            screen = PAUSE;
            audio.setDuck(true);
            audio.play(Audio.CLICK, 0.6f);
            resetInput();
        }
    }

    private void onMove(int id, float x, float y) {
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
        if (screen == TITLE) {
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
            if (Updater.state == Updater.AVAILABLE || Updater.state == Updater.ERROR) {
                if (in(x, y, 150 * s, 26 * s, 270 * s, 36 * s)) { Updater.start(getContext()); return; }
            }
            if (in(x, y, W / 2f, 332 * s, 240 * s, 48 * s)) startRun();
        } else if (screen == PAUSE) {
            if (in(x, y, W / 2f - 130 * s, 340 * s, 220 * s, 46 * s)) {
                screen = PLAY; audio.setDuck(false); audio.play(Audio.CLICK, 0.7f);
            } else if (in(x, y, W / 2f + 130 * s, 340 * s, 220 * s, 46 * s)) {
                audio.play(Audio.CLICK, 0.7f);
                toTitle();
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
                game.viewW = W / (S * Game.ZOOM); game.viewH = H / (S * Game.ZOOM);
                game.update(dt);
                if (game.levelUpPending) lvlT = 0f;
                if (game.over) {
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
        if (screen == TITLE) game.viewW = W / (S * Game.ZOOM); game.viewH = H / (S * Game.ZOOM);

        if (screen == INTRO) {
            introT += dt;
            drawIntro(c);
            if (introT > 7.0f) beginPlay();
            postInvalidateOnAnimation();
            return;
        }

        // ---- draw ----
        render.draw(c, game, W, H, S, uiT);
        if (screen == TITLE) {
            drawTitle(c);
            drawUpdate(c);
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
        pt.setTextSize(size * S);
        pt.setFakeBoldText(bold);
        pt.setColor(Render.a(0xFF000000, 0.55f * ((col >>> 24) / 255f)));
        c.drawText(s, x + 1.2f * S, y + 1.2f * S, pt);
        pt.setColor(col);
        c.drawText(s, x, y, pt);
        pt.setFakeBoldText(false);
        pt.setTextAlign(Paint.Align.LEFT);
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

    private void drawTitle(Canvas c) {
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0x99060616);
        c.drawRect(0, 0, W, H, pt);
        float s = S;
        float pulse = 0.5f + 0.5f * (float) Math.sin(uiT * 2f);
        txt(c, "OURAVEX", W / 2f, 62 * s, 58, Render.a(0xFF35D6FF, 0.35f + 0.1f * pulse), Paint.Align.CENTER, true);
        txt(c, "OURAVEX", W / 2f, 60 * s, 58, 0xFFFFFFFF, Paint.Align.CENTER, true);
        txt(c, "Бий. Телепортуйся. Виживай.", W / 2f, 84 * s, 14, 0xFF9AB0D0, Paint.Align.CENTER, false);
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
        if (bestStage > 0) {
            txt(c, "Рекорд: локація " + bestStage + "  ·  вбито " + bestKills, W / 2f, 384 * s, 12, 0xFF8890B0, Paint.Align.CENTER, false);
        }
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
        bar(c, 14 * s, 12 * s, 200 * s, 13 * s, hpF, hpF < 0.3f ? 0xFFFF4D6D : 0xFF4DFF88);
        txt(c, (int) p.hp + " / " + (int) p.maxHp(), 18 * s, 22.5f * s, 10, 0xFFFFFFFF, Paint.Align.LEFT, true);
        bar(c, 14 * s, 29 * s, 170 * s, 9 * s, p.ki / p.maxKi(), p.transformed ? Data.GOLD : 0xFF35D6FF);
        bar(c, 14 * s, 42 * s, 200 * s, 4 * s, p.xp / p.xpNeed(), 0xFFFFD84A);
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
            case 0: ps = "ПОРТАЛ " + game.kills + "/" + game.killGoal; break;
            case 1: ps = "ПОРТАЛ ГОТОВИЙ"; pc = 0xFF4DFFB0; break;
            case 2: ps = "ДОБИЙ ЗАЛИШКИ!"; pc = 0xFFFF9A3C; break;
            case 3: ps = ""; break;
            default: ps = "ПОРТАЛ ВІДКРИТО"; pc = 0xFFFFD84A; break;
        }
        if (game.portalState == 0) {
            bar(c, W / 2f - 80 * s, 26 * s, 160 * s, 7 * s, game.kills / (float) Math.max(1, game.killGoal), 0xFF7DF9FF);
            txt(c, ps, W / 2f, 46 * s, 11, pc, Paint.Align.CENTER, true);
        } else if (!ps.isEmpty()) {
            txt(c, ps, W / 2f, 36 * s, 14, pc, Paint.Align.CENTER, true);
        }
        // boss bar
        if (game.boss != null) {
            float bw = 420 * s, bxx = W / 2f - bw / 2, byy = 58 * s;
            float f = game.boss.hp / game.boss.maxHp;
            bar(c, bxx, byy, bw, 12 * s, f, game.boss.phase == 1 ? 0xFFFF6A3A : 0xFFFF3050);
            txt(c, Data.BOSS_NAME[game.boss.type == Ent.BOSS1 ? 0 : 1], W / 2f, byy + 26 * s, 13, 0xFFFFFFFF, Paint.Align.CENTER, true);
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
        // dash charges
        for (int i = 0; i < p.dashMax; i++) {
            float frac = Math.max(0f, Math.min(1f, p.dashCharge - i));
            float x = bx[B_L] - 30 * s + i * 17 * s, y = by[B_L] - br[B_L] - 14 * s;
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(Render.a(0xFF000000, 0.5f));
            c.drawCircle(x, y, 6 * s, pt);
            pt.setColor(frac >= 1f ? 0xFF7DF9FF : Render.a(0xFF7DF9FF, 0.35f));
            c.drawCircle(x, y, 6 * s * (0.3f + 0.7f * frac), pt);
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
        for (int i = 0; i < 4; i++) {
            if (!p.unlocked[i]) continue;
            int b = B_SK + i;
            int col = Data.SKILL_COL[i];
            float cost = p.skillCost(i);
            boolean noKi = p.ki < cost && !(i == 3 && p.transformed);
            boolean cd = p.skCd[i] > 0f;
            circleBtn(c, b, col, bDown[b], noKi || cd ? 0.06f : 0.18f);
            glyphSkill(c, p.cls, i, bx[b], by[b], br[b] * 0.55f, noKi ? Render.a(col, 0.4f) : col);
            if (cd) {
                float frac = Math.min(1f, p.skCd[i] / Data.SKILL_CD[p.cls][i]);
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

    private void drawPause(Canvas c) {
        float s = S;
        Player p = game.p;
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0xCC060616);
        c.drawRect(0, 0, W, H, pt);
        txt(c, "ПАУЗА", W / 2f, 50 * s, 34, 0xFFFFFFFF, Paint.Align.CENTER, true);
        // stats panel
        panel(c, W / 2f - 170 * s, 190 * s, 300 * s, 200 * s, 0xFF35D6FF, 0.8f);
        txt(c, Data.CLS_NAME[p.cls] + "  ·  РІВ. " + p.level, W / 2f - 170 * s, 110 * s, 15, Data.CLS_COL[p.cls], Paint.Align.CENTER, true);
        String[] nm = {"СИЛА", "ЖИВУЧІСТЬ", "СПРИТНІСТЬ", "ДУХ", "УДАЧА"};
        float[] vv = {p.str, p.vit, p.agi, p.spi, p.effLuk()};
        for (int i = 0; i < 5; i++) {
            txt(c, nm[i], W / 2f - 300 * s, (134 + i * 16) * s, 12, 0xFFB0B8D8, Paint.Align.LEFT, false);
            txt(c, String.valueOf((int) vv[i]), W / 2f - 150 * s, (134 + i * 16) * s, 12, 0xFFFFFFFF, Paint.Align.RIGHT, true);
        }
        txt(c, "АТАКА +" + (int) ((p.dmgMul() - 1f) * 100f) + "%", W / 2f - 130 * s, 134 * s, 11, 0xFFFFB02E, Paint.Align.LEFT, false);
        txt(c, "ЗАХИСТ " + (int) p.defense(), W / 2f - 130 * s, 150 * s, 11, 0xFF4DFF88, Paint.Align.LEFT, false);
        txt(c, "КРИТ " + (int) (p.critChance() * 100f) + "%", W / 2f - 130 * s, 166 * s, 11, 0xFFFFF04D, Paint.Align.LEFT, false);
        txt(c, "ДРОП x" + String.format("%.2f", p.dropMul()), W / 2f - 130 * s, 182 * s, 11, 0xFFFFD84A, Paint.Align.LEFT, false);
        txt(c, "ШВИДК. +" + (int) ((p.moveSpeed() / 250f - 1f) * 100f) + "%", W / 2f - 130 * s, 198 * s, 11, 0xFF35D6FF, Paint.Align.LEFT, false);
        // items panel
        panel(c, W / 2f + 170 * s, 190 * s, 300 * s, 200 * s, 0xFFFFD84A, 0.8f);
        txt(c, "ПРЕДМЕТИ", W / 2f + 170 * s, 110 * s, 15, 0xFFFFD84A, Paint.Align.CENTER, true);
        int n = 0;
        for (int i = 0; i < Data.ITEMS; i++) {
            if (p.items[i] <= 0) continue;
            float yy = (130 + n * 15) * s;
            if (n < 10) txt(c, Data.ITEM_NAME[i] + " x" + p.items[i], W / 2f + 28 * s, yy, 11, Data.ITEM_COL[i], Paint.Align.LEFT, true);
            n++;
        }
        if (n == 0) txt(c, "Поки порожньо", W / 2f + 170 * s, 190 * s, 12, 0xFF8890B0, Paint.Align.CENTER, false);
        panel(c, W / 2f - 130 * s, 340 * s, 220 * s, 46 * s, 0xFF4DFFB0, 0.9f);
        txt(c, "ПРОДОВЖИТИ", W / 2f - 130 * s, 347 * s, 18, 0xFF4DFFB0, Paint.Align.CENTER, true);
        panel(c, W / 2f + 130 * s, 340 * s, 220 * s, 46 * s, 0xFFFF4D6D, 0.9f);
        txt(c, "В МЕНЮ", W / 2f + 130 * s, 347 * s, 18, 0xFFFF4D6D, Paint.Align.CENTER, true);
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
