package com.foonyresso.ouravex;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

public class GameView extends View {

    private static final int READY = 0;
    private static final int PLAYING = 1;
    private static final int OVER = 2;

    private static class Star { float x, y, speed, size; }
    private static class Bullet { float x, y; }
    private static class Enemy { float x, y, vy, r, phase; int hp, type; }
    private static class Spark { float x, y, vx, vy, life; int color; }

    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shipPath = new Path();
    private final ArrayList<Star> stars = new ArrayList<>();
    private final ArrayList<Bullet> bullets = new ArrayList<>();
    private final ArrayList<Enemy> enemies = new ArrayList<>();
    private final ArrayList<Spark> sparks = new ArrayList<>();
    private final SharedPreferences prefs;

    private final String sTap;
    private final String sHint;
    private final String sOver;
    private final String sScore;
    private final String sBest;
    private final String sRestart;

    private int state = READY;
    private int w, h;
    private float u = 1f;
    private float px, py, tx, ty;
    private int score, best, lives;
    private float invuln, fireTimer, spawnTimer, time, overTime;
    private long lastNanos;

    public GameView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("ouravex", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        sTap = context.getString(R.string.tap_to_start);
        sHint = context.getString(R.string.hint);
        sOver = context.getString(R.string.game_over);
        sScore = context.getString(R.string.score_label);
        sBest = context.getString(R.string.best_label);
        sRestart = context.getString(R.string.tap_restart);
        setKeepScreenOn(true);
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        super.onSizeChanged(nw, nh, ow, oh);
        w = nw;
        h = nh;
        u = nw / 360f;

        shipPath.reset();
        shipPath.moveTo(0, -22 * u);
        shipPath.lineTo(16 * u, 16 * u);
        shipPath.lineTo(0, 9 * u);
        shipPath.lineTo(-16 * u, 16 * u);
        shipPath.close();

        stars.clear();
        for (int i = 0; i < 70; i++) {
            Star s = new Star();
            s.x = rnd.nextFloat() * w;
            s.y = rnd.nextFloat() * h;
            s.speed = (20 + rnd.nextFloat() * 90) * u;
            s.size = (0.6f + rnd.nextFloat() * 1.6f) * u;
            stars.add(s);
        }

        px = w / 2f;
        py = h * 0.8f;
        tx = px;
        ty = py;
    }

    private void resetGame() {
        bullets.clear();
        enemies.clear();
        sparks.clear();
        score = 0;
        lives = 3;
        time = 0;
        fireTimer = 0;
        spawnTimer = 0.6f;
        invuln = 1.5f;
        px = w / 2f;
        py = h * 0.8f;
        tx = px;
        ty = py;
        state = PLAYING;
    }

    private void gameOver() {
        state = OVER;
        overTime = 0;
        explode(px, py, 0xFF66E0FF, 40);
        if (score > best) {
            best = score;
            prefs.edit().putInt("best", best).apply();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (state == READY || (state == OVER && overTime > 0.7f)) {
                    resetGame();
                }
                tx = e.getX();
                ty = e.getY() - 70 * u;
                break;
            case MotionEvent.ACTION_MOVE:
                tx = e.getX();
                ty = e.getY() - 70 * u;
                break;
            default:
                break;
        }
        return true;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private void addBullet(float x, float y) {
        Bullet b = new Bullet();
        b.x = x;
        b.y = y;
        bullets.add(b);
    }

    private void spawnEnemy() {
        Enemy e = new Enemy();
        boolean tank = rnd.nextFloat() < Math.min(0.35f, time * 0.004f);
        e.type = tank ? 1 : 0;
        e.hp = tank ? 4 : 1;
        e.r = (tank ? 23 : 15) * u;
        e.x = e.r + rnd.nextFloat() * (w - 2 * e.r);
        e.y = -e.r;
        float speed = Math.min(260f, 95f + rnd.nextFloat() * 55f + time * 2.2f);
        e.vy = speed * u * (tank ? 0.6f : 1f);
        e.phase = rnd.nextFloat() * 6.28f;
        enemies.add(e);
    }

    private void explode(float x, float y, int color, int n) {
        for (int i = 0; i < n; i++) {
            Spark s = new Spark();
            double a = rnd.nextDouble() * Math.PI * 2;
            float sp = (50 + rnd.nextFloat() * 190) * u;
            s.x = x;
            s.y = y;
            s.vx = (float) Math.cos(a) * sp;
            s.vy = (float) Math.sin(a) * sp;
            s.life = 0.35f + rnd.nextFloat() * 0.55f;
            s.color = color;
            sparks.add(s);
        }
    }

    private void updateSparks(float dt) {
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark s = sparks.get(i);
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.life -= dt;
            if (s.life <= 0) sparks.remove(i);
        }
    }

    private void update(float dt) {
        for (Star s : stars) {
            s.y += s.speed * dt;
            if (s.y > h) {
                s.y = -4 * u;
                s.x = rnd.nextFloat() * w;
            }
        }
        updateSparks(dt);
        if (state == OVER) overTime += dt;
        if (state != PLAYING) return;

        time += dt;
        float k = Math.min(1f, 14f * dt);
        px += (tx - px) * k;
        py += (ty - py) * k;
        px = clamp(px, 18 * u, w - 18 * u);
        py = clamp(py, 90 * u, h - 30 * u);
        if (invuln > 0) invuln -= dt;

        // auto-fire, more guns as score grows
        fireTimer -= dt;
        if (fireTimer <= 0) {
            fireTimer = 0.17f;
            int level = 1 + Math.min(2, score / 1500);
            if (level == 1) {
                addBullet(px, py - 24 * u);
            } else if (level == 2) {
                addBullet(px - 8 * u, py - 20 * u);
                addBullet(px + 8 * u, py - 20 * u);
            } else {
                addBullet(px, py - 26 * u);
                addBullet(px - 13 * u, py - 18 * u);
                addBullet(px + 13 * u, py - 18 * u);
            }
        }

        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.y -= 640 * u * dt;
            if (b.y < -20 * u) bullets.remove(i);
        }

        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            float interval = Math.max(0.35f, 1.0f - time * 0.01f);
            spawnTimer = interval * (0.7f + rnd.nextFloat() * 0.6f);
            spawnEnemy();
        }

        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            e.y += e.vy * dt;
            e.x += (float) Math.sin(time * 2.2f + e.phase) * 45 * u * dt;
            e.x = clamp(e.x, e.r, w - e.r);
            if (e.y - e.r > h) enemies.remove(i);
        }

        // bullets vs enemies
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            for (int j = bullets.size() - 1; j >= 0; j--) {
                Bullet b = bullets.get(j);
                float dx = b.x - e.x;
                float dy = b.y - e.y;
                float rr = e.r + 4 * u;
                if (dx * dx + dy * dy < rr * rr) {
                    bullets.remove(j);
                    e.hp--;
                    if (e.hp <= 0) {
                        score += e.type == 1 ? 30 : 10;
                        explode(e.x, e.y, e.type == 1 ? 0xFFFFB020 : 0xFFFF4D6D,
                                e.type == 1 ? 26 : 14);
                        enemies.remove(i);
                        break;
                    }
                }
            }
        }

        // player vs enemies
        if (invuln <= 0) {
            for (int i = enemies.size() - 1; i >= 0; i--) {
                Enemy e = enemies.get(i);
                float dx = px - e.x;
                float dy = py - e.y;
                float rr = e.r + 11 * u;
                if (dx * dx + dy * dy < rr * rr) {
                    explode(e.x, e.y, 0xFFFFFFFF, 18);
                    enemies.remove(i);
                    lives--;
                    invuln = 1.8f;
                    if (lives <= 0) gameOver();
                    break;
                }
            }
        }
    }

    private void text(Canvas c, String s, float y, float size, int color, boolean bold) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setFakeBoldText(bold);
        c.drawText(s, w / 2f, y, paint);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void render(Canvas c) {
        c.drawColor(0xFF080818);
        paint.setStyle(Paint.Style.FILL);

        for (Star s : stars) {
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha(80 + (int) (s.size / u * 60));
            c.drawCircle(s.x, s.y, s.size, paint);
        }

        paint.setColor(0xFF7DF9FF);
        for (Bullet b : bullets) {
            c.drawRect(b.x - 2 * u, b.y - 9 * u, b.x + 2 * u, b.y + 9 * u, paint);
        }

        for (Enemy e : enemies) {
            if (e.type == 1) {
                paint.setColor(0xFFFFB020);
                c.drawCircle(e.x, e.y, e.r, paint);
                paint.setColor(0xFF3A2400);
                c.drawCircle(e.x, e.y, e.r * 0.62f, paint);
                paint.setColor(0xFFFFB020);
                c.drawCircle(e.x, e.y, e.r * 0.62f * e.hp / 4f, paint);
            } else {
                paint.setColor(0xFFFF4D6D);
                c.drawCircle(e.x, e.y, e.r, paint);
                paint.setColor(0xFF2A0A14);
                c.drawCircle(e.x, e.y, e.r * 0.45f, paint);
            }
        }

        boolean blinkHidden = invuln > 0 && ((int) (invuln * 12)) % 2 == 0;
        if (state != OVER && !blinkHidden) {
            c.save();
            c.translate(px, py);
            paint.setColor(0xFFFF9A3C);
            float f = (10 + rnd.nextFloat() * 8) * u;
            c.drawRect(-3 * u, 10 * u, 3 * u, 10 * u + f, paint);
            paint.setColor(0xFF66E0FF);
            c.drawPath(shipPath, paint);
            c.restore();
        }

        for (Spark s : sparks) {
            paint.setColor(s.color);
            paint.setAlpha((int) (Math.min(1f, s.life * 2f) * 255));
            c.drawCircle(s.x, s.y, 2.2f * u, paint);
        }

        if (state != READY) {
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setFakeBoldText(true);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(20 * u);
            c.drawText(sScore + ": " + score, 16 * u, 40 * u, paint);
            paint.setFakeBoldText(false);
            paint.setColor(0xFF8899BB);
            paint.setTextSize(13 * u);
            c.drawText(sBest + ": " + Math.max(best, score), 16 * u, 60 * u, paint);
            paint.setColor(0xFFFF4D6D);
            for (int i = 0; i < lives; i++) {
                c.drawCircle(w - 20 * u - i * 20 * u, 34 * u, 7 * u, paint);
            }
        }

        if (state == READY) {
            text(c, "OURAVEX", h * 0.38f, 44 * u, 0xFF66E0FF, true);
            text(c, sTap, h * 0.38f + 50 * u, 20 * u, 0xFFFFFFFF, false);
            text(c, sHint, h * 0.38f + 80 * u, 14 * u, 0xFF8899BB, false);
            text(c, sBest + ": " + best, h * 0.38f + 115 * u, 14 * u, 0xFF8899BB, false);
        } else if (state == OVER) {
            paint.setColor(0x99000000);
            c.drawRect(0, 0, w, h, paint);
            text(c, sOver, h * 0.36f, 34 * u, 0xFFFF4D6D, true);
            text(c, sScore + ": " + score, h * 0.36f + 48 * u, 22 * u, 0xFFFFFFFF, false);
            text(c, sBest + ": " + best, h * 0.36f + 78 * u, 16 * u, 0xFF8899BB, false);
            if (overTime > 0.7f && ((int) (overTime * 2)) % 2 == 0) {
                text(c, sRestart, h * 0.36f + 130 * u, 18 * u, 0xFFFFFFFF, false);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : (now - lastNanos) / 1e9f;
        lastNanos = now;
        if (dt > 0.05f) dt = 0.05f;
        if (w > 0 && h > 0) {
            update(dt);
            render(canvas);
        }
        postInvalidateOnAnimation();
    }
}
