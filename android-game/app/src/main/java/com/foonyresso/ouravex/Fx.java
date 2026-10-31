package com.foonyresso.ouravex;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import java.util.Random;

/** Particles, floating text, afterimages, screen shake / flash. All world-space. */
public final class Fx {
    public static final int SPARK = 0, DOT = 1, RING = 2, FLASH = 3, SMOKE = 4;
    private static final int MAX = 900;
    private static final int TMAX = 40;
    private static final int GMAX = 16;

    private final float[] x = new float[MAX], y = new float[MAX], vx = new float[MAX], vy = new float[MAX];
    private final float[] life = new float[MAX], maxLife = new float[MAX];
    private final float[] s0 = new float[MAX], s1 = new float[MAX], drag = new float[MAX], grav = new float[MAX];
    private final int[] col = new int[MAX];
    private final byte[] type = new byte[MAX];
    private int n;

    private final float[] tx = new float[TMAX], ty = new float[TMAX], tvy = new float[TMAX];
    private final float[] tlife = new float[TMAX], tmax = new float[TMAX], tsize = new float[TMAX];
    private final String[] ts = new String[TMAX];
    private final int[] tc = new int[TMAX];
    private int tn;

    private final float[][] gPose = new float[GMAX][Rig.N];
    private final float[] gx = new float[GMAX], gy = new float[GMAX], glife = new float[GMAX], gmax = new float[GMAX];
    private final int[] gface = new int[GMAX], gcol = new int[GMAX], ghc = new int[GMAX];
    private final float[] ghair = new float[GMAX];
    private int gn;

    private final Random rnd = new Random();
    private final PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);

    public float trauma;
    public float shakeX, shakeY;
    public int flashColor = 0xFFFFFFFF;
    public float flashA;

    public float rnd() { return rnd.nextFloat(); }
    public float rnd(float a, float b) { return a + rnd.nextFloat() * (b - a); }

    public void clear() { n = 0; tn = 0; gn = 0; trauma = 0; flashA = 0; }

    public void add(int t, float px, float py, float pvx, float pvy, float lifeT, float size0, float size1,
                    int color, float dragK, float gravK) {
        if (n >= MAX) return;
        int i = n++;
        type[i] = (byte) t;
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy;
        life[i] = lifeT; maxLife[i] = lifeT;
        float k = (t == RING || t == FLASH) ? 1.6f : 1.9f;
        s0[i] = size0 * k; s1[i] = size1 * k; col[i] = color; drag[i] = dragK; grav[i] = gravK;
    }

    // ---------- presets ----------

    public void sparks(float px, float py, int cnt, float spd, float lifeT, float size, int color,
                       float dirX, float dirY, float spread) {
        float base = (float) Math.atan2(dirY, dirX);
        for (int i = 0; i < cnt; i++) {
            float a = base + (rnd.nextFloat() - 0.5f) * spread;
            float s = spd * (0.35f + rnd.nextFloat() * 0.9f);
            add(SPARK, px, py, (float) Math.cos(a) * s, (float) Math.sin(a) * s,
                    lifeT * (0.5f + rnd.nextFloat() * 0.7f), size, 0f, color, 2.2f, 0f);
        }
    }

    public void ring(float px, float py, float r0, float r1, float lifeT, int color) {
        add(RING, px, py, 0, 0, lifeT, r0, r1, color, 0, 0);
    }

    public void flash(float px, float py, float r0, float r1, float lifeT, int color) {
        add(FLASH, px, py, 0, 0, lifeT, r0, r1, color, 0, 0);
    }

    public void hit(float px, float py, float dir, float power, int color) {
        flash(px, py, 14f + 22f * power, 4f, 0.10f, 0xFFFFFFFF);
        ring(px, py, 6f, 34f + 46f * power, 0.22f, color);
        sparks(px, py, (int) (8 + 14 * power), 380f + 260f * power, 0.28f, 3f, color, dir, -0.15f, 2.3f);
        sparks(px, py, 3 + (int) (4 * power), 700f, 0.12f, 4f, 0xFFFFFFFF, dir, 0f, 0.8f);
        for (int i = 0; i < 3 + (int) (4 * power); i++) {
            add(DOT, px, py, dir * rnd(60f, 260f), rnd(-260f, -60f), rnd(0.3f, 0.6f), 3f, 0f, color, 1.5f, 700f);
        }
        // slash streak
        add(SPARK, px, py, dir * 1400f, -90f, 0.09f, 5f, 0f, 0xFFFFFFFF, 0f, 0f);
    }

    public void explosion(float px, float py, float r, int color) {
        flash(px, py, r * 0.9f, r * 0.2f, 0.16f, 0xFFFFFFFF);
        flash(px, py, r * 0.6f, r * 1.3f, 0.30f, color);
        ring(px, py, r * 0.2f, r * 1.5f, 0.35f, color);
        ring(px, py, r * 0.1f, r * 1.0f, 0.25f, 0xFFFFFFFF);
        sparks(px, py, 26, r * 5f, 0.5f, 3.5f, color, 0f, 0f, 6.3f);
        sparks(px, py, 10, r * 7f, 0.25f, 4f, 0xFFFFFFFF, 0f, 0f, 6.3f);
        for (int i = 0; i < 12; i++) {
            add(SMOKE, px + rnd(-r * 0.3f, r * 0.3f), py + rnd(-r * 0.3f, r * 0.3f), rnd(-60f, 60f),
                    rnd(-90f, 10f), rnd(0.5f, 0.9f), r * 0.2f, r * 0.45f, 0xFF2A2236, 1.5f, -20f);
        }
        trauma = Math.min(1f, trauma + 0.12f + r * 0.002f);
    }

    /** vertical light pillar for teleports */
    public void blink(float px, float py, int color) {
        for (int i = 0; i < 10; i++) {
            add(SPARK, px + rnd(-9f, 9f), py - rnd(0f, 80f), 0f, -rnd(900f, 1700f), 0.16f, rnd(3f, 6f), 0f,
                    i % 2 == 0 ? 0xFFFFFFFF : color, 0f, 0f);
        }
        ring(px, py - 4f, 4f, 52f, 0.2f, color);
        flash(px, py - 40f, 26f, 8f, 0.12f, 0xFFFFFFFF);
        for (int i = 0; i < 6; i++) {
            add(DOT, px + rnd(-14f, 14f), py - rnd(0f, 60f), rnd(-40f, 40f), -rnd(60f, 200f), 0.35f, 4f, 0f, color, 1f, 0f);
        }
    }

    public void dust(float px, float py, float dir, int cnt) {
        for (int i = 0; i < cnt; i++) {
            add(SMOKE, px + rnd(-6f, 6f), py - 2f, -dir * rnd(20f, 90f) + rnd(-20f, 20f), -rnd(10f, 50f),
                    rnd(0.25f, 0.5f), 4f, 9f, 0xFF3A3450, 3f, 0f);
        }
    }

    public void aura(float px, float py, int color, float intensity) {
        int cnt = intensity > 1.5f ? 3 : 2;
        for (int i = 0; i < cnt; i++) {
            add(DOT, px + rnd(-16f, 16f), py - rnd(0f, 70f), rnd(-18f, 18f), -rnd(70f, 170f), rnd(0.35f, 0.7f),
                    rnd(3f, 6f) * intensity, 0f, color, 0.5f, 0f);
        }
        if (rnd.nextFloat() < 0.35f) {
            add(SPARK, px + rnd(-14f, 14f), py - rnd(0f, 40f), 0f, -rnd(300f, 600f), 0.25f, 2.5f, 0f, 0xFFFFFFFF, 0f, 0f);
        }
    }

    public void text(float px, float py, String s, int color, float size) {
        size *= 2.1f;
        int i;
        if (tn < TMAX) i = tn++;
        else i = 0;
        tx[i] = px; ty[i] = py; tvy[i] = -70f; tlife[i] = 0.8f; tmax[i] = 0.8f;
        tsize[i] = size; ts[i] = s; tc[i] = color;
    }

    public void ghost(float[] pose, float px, float py, int facing, int color, float hair, int hairColor, float lifeT) {
        int i;
        if (gn < GMAX) i = gn++;
        else i = 0;
        Rig.copy(gPose[i], pose);
        gx[i] = px; gy[i] = py; gface[i] = facing; gcol[i] = color;
        ghair[i] = hair; ghc[i] = hairColor; glife[i] = lifeT; gmax[i] = lifeT;
    }

    public void shake(float amt) { trauma = Math.min(1f, trauma + amt); }

    public void screenFlash(int color, float a) { flashColor = color; flashA = Math.max(flashA, a); }

    // ---------- update / render ----------

    private void moveParticle(int from, int to) {
        type[to] = type[from]; x[to] = x[from]; y[to] = y[from]; vx[to] = vx[from]; vy[to] = vy[from];
        life[to] = life[from]; maxLife[to] = maxLife[from]; s0[to] = s0[from]; s1[to] = s1[from];
        col[to] = col[from]; drag[to] = drag[from]; grav[to] = grav[from];
    }

    public void update(float dt) {
        for (int i = n - 1; i >= 0; i--) {
            life[i] -= dt;
            if (life[i] <= 0f) {
                n--;
                if (i != n) moveParticle(n, i);
                continue;
            }
            float k = Math.max(0f, 1f - drag[i] * dt);
            vx[i] *= k;
            vy[i] = vy[i] * k + grav[i] * dt;
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
        }
        for (int i = tn - 1; i >= 0; i--) {
            tlife[i] -= dt;
            if (tlife[i] <= 0f) {
                tn--;
                if (i != tn) {
                    tx[i] = tx[tn]; ty[i] = ty[tn]; tvy[i] = tvy[tn]; tlife[i] = tlife[tn];
                    tmax[i] = tmax[tn]; tsize[i] = tsize[tn]; ts[i] = ts[tn]; tc[i] = tc[tn];
                }
                continue;
            }
            ty[i] += tvy[i] * dt;
            tvy[i] *= Math.max(0f, 1f - 2.5f * dt);
        }
        for (int i = gn - 1; i >= 0; i--) {
            glife[i] -= dt;
            if (glife[i] <= 0f) {
                gn--;
                if (i != gn) {
                    Rig.copy(gPose[i], gPose[gn]);
                    gx[i] = gx[gn]; gy[i] = gy[gn]; glife[i] = glife[gn]; gmax[i] = gmax[gn];
                    gface[i] = gface[gn]; gcol[i] = gcol[gn]; ghair[i] = ghair[gn]; ghc[i] = ghc[gn];
                }
            }
        }
        trauma = Math.max(0f, trauma - dt * 1.7f);
        float t2 = trauma * trauma;
        shakeX = (rnd.nextFloat() * 2f - 1f) * 22f * t2;
        shakeY = (rnd.nextFloat() * 2f - 1f) * 16f * t2;
        flashA = Math.max(0f, flashA - dt * 3.2f);
    }

    private static int alphaCol(int c, float a) {
        int al = (int) (Math.max(0f, Math.min(1f, a)) * 255f);
        return (c & 0x00FFFFFF) | (al << 24);
    }

    /** ghosts + smoke (under entities) */
    public void renderBack(Canvas c, Paint p) {
        for (int i = 0; i < gn; i++) {
            float a = glife[i] / gmax[i] * 0.55f;
            Rig.draw(c, p, gx[i], gy[i], gface[i], gPose[i], gcol[i], a, false, ghair[i], ghc[i]);
        }
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            if (type[i] != SMOKE) continue;
            float k = 1f - life[i] / maxLife[i];
            p.setColor(alphaCol(col[i], (1f - k) * 0.55f));
            c.drawCircle(x[i], y[i], s0[i] + (s1[i] - s0[i]) * k, p);
        }
    }

    /** additive glow particles + texts (over entities) */
    public void renderFront(Canvas c, Paint p) {
        p.setXfermode(add);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < n; i++) {
            int t = type[i];
            if (t == SMOKE) continue;
            float k = 1f - life[i] / maxLife[i];
            float a = 1f - k;
            float sz = s0[i] + (s1[i] - s0[i]) * k;
            switch (t) {
                case SPARK:
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(0.8f, s0[i] * a));
                    p.setColor(alphaCol(col[i], a * 1.1f));
                    c.drawLine(x[i], y[i], x[i] - vx[i] * 0.045f, y[i] - vy[i] * 0.045f, p);
                    break;
                case DOT:
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alphaCol(col[i], a));
                    c.drawCircle(x[i], y[i], Math.max(0.5f, sz * (0.4f + 0.6f * a)), p);
                    break;
                case RING:
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1.5f + 5f * a);
                    p.setColor(alphaCol(col[i], a));
                    c.drawCircle(x[i], y[i], Math.max(1f, sz), p);
                    break;
                default: // FLASH
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alphaCol(col[i], a * 0.85f));
                    c.drawCircle(x[i], y[i], Math.max(1f, sz), p);
                    break;
            }
        }
        p.setXfermode(null);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < tn; i++) {
            float a = tlife[i] / tmax[i];
            float pop = 1f + Math.max(0f, a - 0.8f) * 2.5f;
            p.setFakeBoldText(true);
            p.setTextSize(tsize[i] * pop);
            p.setColor(alphaCol(0xFF000000, Math.min(1f, a * 2f) * 0.6f));
            c.drawText(ts[i], tx[i] + 1.5f, ty[i] + 1.5f, p);
            p.setColor(alphaCol(tc[i], Math.min(1f, a * 2f)));
            c.drawText(ts[i], tx[i], ty[i], p);
        }
        p.setFakeBoldText(false);
        p.setTextAlign(Paint.Align.LEFT);
    }
}
