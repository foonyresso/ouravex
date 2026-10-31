package com.foonyresso.ouravex;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import com.foonyresso.ouravex.Ent.Burst;
import com.foonyresso.ouravex.Ent.Chest;
import com.foonyresso.ouravex.Ent.Drop;
import com.foonyresso.ouravex.Ent.Enemy;
import com.foonyresso.ouravex.Ent.Platform;
import com.foonyresso.ouravex.Ent.Proj;
import com.foonyresso.ouravex.Ent.Tele;

/** World renderer (everything except HUD / menus). */
final class Render {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private Shader sky;
    private int skyPal = -1, skyH = -1;

    static int a(int col, float al) {
        int v = (int) (Math.max(0f, Math.min(1f, al)) * 255f);
        return (col & 0x00FFFFFF) | (v << 24);
    }

    private void gl(Canvas c, float x0, float y0, float x1, float y1, int col, float w) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(w + 6f);
        p.setColor(a(col, 0.22f));
        c.drawLine(x0, y0, x1, y1, p);
        p.setStrokeWidth(w);
        p.setColor(col);
        c.drawLine(x0, y0, x1, y1, p);
    }

    private void line(Canvas c, float x0, float y0, float x1, float y1, int col, float w) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(w);
        p.setColor(col);
        c.drawLine(x0, y0, x1, y1, p);
    }

    private void circ(Canvas c, float x, float y, float r, int col, boolean fill, float w) {
        p.setStyle(fill ? Paint.Style.FILL : Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setColor(col);
        c.drawCircle(x, y, r, p);
    }

    private void hex(float cx, float cy, float r, float rot) {
        path.reset();
        for (int i = 0; i < 6; i++) {
            float an = rot + i * 1.0472f;
            float x = cx + (float) Math.cos(an) * r, y = cy + (float) Math.sin(an) * r;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
    }

    // ------------------------------------------------------------------

    void draw(Canvas c, Game g, int W, int H, float S, float t) {
        Player pl = g.p;
        float groundY = H * 0.75f;
        drawBackground(c, g, W, H, groundY, t);

        c.save();
        float zoom = 1f + g.zoomKick;
        c.translate(W * 0.5f, H * 0.5f);
        c.scale(zoom, zoom);
        c.translate(-W * 0.5f, -H * 0.5f);
        c.scale(S * Game.ZOOM, S * Game.ZOOM);
        c.translate(-g.camX + g.fx.shakeX, -g.camY + g.fx.shakeY);

        drawGround(c, g, g.viewW, t);
        for (int i = 0; i < g.plats.size(); i++) {
            Platform pf = g.plats.get(i);
            if (pf.x + pf.w < g.camX - 50f || pf.x > g.camX + g.viewW + 50f || pf.y < g.camY - 50f || pf.y > g.camY + g.viewH + 50f) continue;
            drawPlatform(c, g, pf);
        }
        drawPortal(c, g, t);
        for (int i = 0; i < g.chests.size(); i++) drawChest(c, g.chests.get(i), t);
        for (int i = 0; i < g.drops.size(); i++) drawDrop(c, g.drops.get(i), t);
        for (int i = 0; i < g.teles.size(); i++) drawTele(c, g.teles.get(i), t);
        g.fx.renderBack(c, p);
        for (int i = 0; i < g.enemies.size(); i++) drawEnemy(c, g, g.enemies.get(i), t);
        drawPlayer(c, g, t);
        for (int i = 0; i < g.projs.size(); i++) drawProj(c, g.projs.get(i), t);
        if (pl.beamOn) drawBeam(c, pl, t);
        for (int i = 0; i < g.bursts.size(); i++) drawBurst(c, g.bursts.get(i));
        g.fx.renderFront(c, p);
        c.restore();
    }

    // ---------------- background ----------------

    private void drawBackground(Canvas c, Game g, int W, int H, float groundY, float t) {
        int[] pal = Data.PAL[g.palIdx];
        if (sky == null || skyPal != g.palIdx || skyH != H) {
            sky = new LinearGradient(0f, 0f, 0f, groundY, pal[0], pal[1], Shader.TileMode.CLAMP);
            skyPal = g.palIdx;
            skyH = H;
        }
        p.setStyle(Paint.Style.FILL);
        p.setShader(sky);
        c.drawRect(0f, 0f, W, H, p);
        p.setShader(null);
        // big glowing moon
        float mx = W * 0.72f - g.camX * 0.02f, my = H * 0.32f, mr = H * 0.16f;
        for (int i = 3; i >= 0; i--) {
            p.setColor(a(pal[2], 0.05f + 0.05f * (3 - i)));
            c.drawCircle(mx, my, mr * (1f + i * 0.35f), p);
        }
        p.setColor(a(0xFFFFFFFF, 0.55f));
        c.drawCircle(mx, my, mr * 0.78f, p);
        // stars
        for (int i = 0; i < 70; i++) {
            float sx = (((i * 127) % 1000) / 1000f) * W - g.camX * 0.03f * (1f + (i % 3) * 0.3f);
            sx = ((sx % W) + W) % W;
            float sy = (((i * 389) % 1000) / 1000f) * groundY * 0.7f;
            float tw = 0.4f + 0.6f * (0.5f + 0.5f * (float) Math.sin(t * 2f + i));
            p.setColor(a(0xFFFFFFFF, 0.55f * tw));
            c.drawCircle(sx, sy, 1f + (i % 3) * 0.6f, p);
        }
        ridge(c, g, W, groundY, 0.12f, H * 0.30f, H * 0.07f, a(pal[1], 0.9f), a(pal[2], 0.35f), 1f);
        ridge(c, g, W, groundY, 0.30f, H * 0.17f, H * 0.05f, a(pal[3], 0.95f), a(pal[2], 0.55f), 2f);
    }

    private void ridge(Canvas c, Game g, int W, float groundY, float k, float base, float amp, int fill, int edge, float seed) {
        float off = g.camX * k * (W / 800f);
        path.reset();
        path.moveTo(0f, groundY + 4f);
        for (int x = 0; x <= W + 24; x += 24) {
            float wx = x + off;
            float h = base + amp * (float) (Math.sin(wx * 0.011f * seed) + 0.6 * Math.sin(wx * 0.027f + seed)
                    + 0.3 * Math.sin(wx * 0.061f));
            float y = groundY - h;
            path.lineTo(x, y);
        }
        path.lineTo(W + 24f, groundY + 4f);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(edge);
        path.reset();
        for (int x = 0; x <= W + 24; x += 24) {
            float wx = x + off;
            float h = base + amp * (float) (Math.sin(wx * 0.011f * seed) + 0.6 * Math.sin(wx * 0.027f + seed)
                    + 0.3 * Math.sin(wx * 0.061f));
            if (x == 0) path.moveTo(x, groundY - h); else path.lineTo(x, groundY - h);
        }
        c.drawPath(path, p);
    }

    // ---------------- ground / props ----------------

    private void drawGround(Canvas c, Game g, float viewW, float t) {
        int[] pal = Data.PAL[g.palIdx];
        float x0 = g.camX - 60f, x1 = g.camX + viewW + 60f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(pal[3]);
        c.drawRect(x0, 0f, x1, 140f, p);
        // grid
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        for (int i = 1; i <= 3; i++) {
            p.setColor(a(pal[2], 0.22f / i));
            float y = i * i * 6f;
            c.drawLine(x0, y, x1, y, p);
        }
        float start = (float) Math.floor(x0 / 90f) * 90f;
        p.setColor(a(pal[2], 0.14f));
        for (float x = start; x < x1; x += 90f) {
            float lean = (x - (g.camX + viewW * 0.5f)) * 0.35f;
            c.drawLine(x, 0f, x + lean, 140f, p);
        }
        gl(c, x0, 0f, x1, 0f, pal[2], 2.5f);
        // world edges
        if (g.camX < 40f) gl(c, 0f, 0f, 0f, -Game.WORLD_H, a(pal[2], 0.6f), 2f);
        if (g.camX + viewW > Game.WORLD_W - 40f) gl(c, Game.WORLD_W, 0f, Game.WORLD_W, -Game.WORLD_H, a(pal[2], 0.6f), 2f);
    }

    private void drawPlatform(Canvas c, Game g, Platform pl) {
        int[] pal = Data.PAL[g.palIdx];
        p.setStyle(Paint.Style.FILL);
        p.setColor(a(pal[3], 0.95f));
        c.drawRect(pl.x, pl.y, pl.x + pl.w, pl.y + 9f, p);
        gl(c, pl.x, pl.y, pl.x + pl.w, pl.y, pal[2], 2.5f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(a(pal[2], 0.18f));
        for (float x = pl.x + 12f; x < pl.x + pl.w - 6f; x += 24f) c.drawLine(x, pl.y + 9f, x + 7f, pl.y + 38f, p);
    }

    private void drawPortal(Canvas c, Game g, float t) {
        float cx = g.portalX, cy = -64f;
        int st = g.portalState;
        int col = st == 0 ? 0xFF4A9ABF : (st == 1 ? 0xFF4DFFB0 : (st == 2 ? 0xFFFF9A3C : (st == 3 ? 0xFF8A3A2A : 0xFFFFD84A)));
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 5f);
        rf.set(cx - 40f, cy - 62f, cx + 40f, cy + 62f);
        if (st == 1 || st == 4 || st == 2) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(a(col, 0.12f + 0.12f * pulse));
            c.drawOval(rf, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(9f);
        p.setColor(a(col, 0.2f));
        c.drawOval(rf, p);
        p.setStrokeWidth(4f);
        p.setColor(a(col, st == 0 ? 0.5f : 1f));
        c.drawOval(rf, p);
        if (st == 0) {
            float frac = g.killGoal > 0 ? Math.min(1f, g.kills / (float) g.killGoal) : 0f;
            rf.set(cx - 40f, cy - 62f, cx + 40f, cy + 62f);
            p.setStrokeWidth(6f);
            p.setColor(0xFF7DF9FF);
            c.drawArc(rf, -90f, 360f * frac, false, p);
        }
        rf.set(cx - 28f, cy - 46f, cx + 28f, cy + 46f);
        p.setStrokeWidth(2.5f);
        p.setColor(a(col, 0.8f));
        c.drawArc(rf, t * 140f, 110f, false, p);
        c.drawArc(rf, t * 140f + 180f, 110f, false, p);
        rf.set(cx - 16f, cy - 28f, cx + 16f, cy + 28f);
        p.setColor(a(col, 0.6f));
        c.drawArc(rf, -t * 200f, 90f, false, p);
        gl(c, cx - 46f, 0f, cx + 46f, 0f, a(col, 0.7f), 3f);
    }

    private void drawChest(Canvas c, Chest ch, float t) {
        float x = ch.x;
        c.save();
        c.translate(0f, ch.y);
        int col = ch.open ? 0xFF6A5A30 : 0xFFFFD84A;
        float pulse = ch.open ? 0f : 0.5f + 0.5f * (float) Math.sin(t * 4f + x);
        if (!ch.open) {
            circ(c, x, -14f, 30f + 6f * pulse, a(col, 0.10f), true, 1f);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF1C1408);
        c.drawRect(x - 18f, -22f, x + 18f, 0f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(col);
        c.drawRect(x - 18f, -22f, x + 18f, 0f, p);
        if (ch.open) {
            line(c, x - 18f, -22f, x - 12f, -36f, col, 3f);
            line(c, x - 12f, -36f, x + 24f, -30f, col, 3f);
        } else {
            line(c, x - 18f, -14f, x + 18f, -14f, col, 2f);
            circ(c, x, -14f, 3.5f, 0xFFFFFFFF, true, 1f);
        }
        c.restore();
    }

    private void drawDrop(Canvas c, Drop d, float t) {
        if (d.type == 2) {
            int col = Data.ITEM_COL[d.item];
            line(c, d.x, d.y, d.x, d.y - 90f, a(col, 0.28f), 8f);
            line(c, d.x, d.y, d.x, d.y - 90f, a(col, 0.6f), 2f);
            float bob = (float) Math.sin(t * 4f + d.x) * 3f;
            float r = 9f;
            path.reset();
            path.moveTo(d.x, d.y - r + bob);
            path.lineTo(d.x + r * 0.75f, d.y + bob);
            path.lineTo(d.x, d.y + r + bob);
            path.lineTo(d.x - r * 0.75f, d.y + bob);
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(a(col, 0.35f));
            c.drawCircle(d.x, d.y + bob, 16f, p);
            p.setColor(col);
            c.drawPath(path, p);
            circ(c, d.x, d.y + bob, 3f, 0xFFFFFFFF, true, 1f);
        } else {
            int col = d.type == 0 ? 0xFF4DFF88 : 0xFF4DA6FF;
            circ(c, d.x, d.y, 10f, a(col, 0.25f), true, 1f);
            circ(c, d.x, d.y, 5f, col, true, 1f);
            circ(c, d.x - 1.5f, d.y - 1.5f, 1.8f, 0xFFFFFFFF, true, 1f);
        }
    }

    // ---------------- telegraphs / bursts ----------------

    private void drawTele(Canvas c, Tele tl, float t) {
        float k = 1f - tl.t / Math.max(0.01f, tl.tmax);
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 22f);
        int col = tl.color;
        if (tl.kind == 1 && tl.shape == 0) {
            circ(c, tl.x, tl.y, 14f + 34f * k, a(col, 0.6f * (1f - k) + 0.2f), false, 3f);
            return;
        }
        if (tl.shape == 0) {
            circ(c, tl.x, tl.y, tl.w, a(col, 0.08f + 0.10f * pulse), true, 1f);
            circ(c, tl.x, tl.y, tl.w, a(col, 0.8f), false, 3f);
            circ(c, tl.x, tl.y, tl.w * k, a(col, 0.55f), false, 3f);
            circ(c, tl.x, tl.y, tl.w * k, a(col, 0.14f), true, 1f);
        } else if (tl.shape == 1) {
            float len = tl.kind == 1 ? 650f : tl.len;
            c.save();
            c.translate(tl.x, tl.y);
            c.rotate(tl.ang * 57.2958f);
            p.setStyle(Paint.Style.FILL);
            if (tl.kind == 1) {
                p.setColor(a(col, 0.55f + 0.25f * pulse));
                c.drawRect(0f, -1.5f, len, 1.5f, p);
            } else {
                p.setColor(a(col, 0.08f + 0.08f * pulse));
                c.drawRect(0f, -tl.w * 0.5f, len, tl.w * 0.5f, p);
                p.setColor(a(col, 0.30f + 0.15f * k));
                c.drawRect(0f, -tl.w * 0.5f * k, len, tl.w * 0.5f * k, p);
                p.setColor(a(col, 0.85f));
                c.drawRect(0f, -tl.w * 0.5f - 1.5f, len, -tl.w * 0.5f + 1.5f, p);
                c.drawRect(0f, tl.w * 0.5f - 1.5f, len, tl.w * 0.5f + 1.5f, p);
            }
            c.restore();
        } else {
            p.setStyle(Paint.Style.FILL);
            p.setColor(a(col, 0.08f + 0.08f * pulse));
            c.drawRect(tl.x - tl.w * 0.5f, tl.y - 700f, tl.x + tl.w * 0.5f, tl.y, p);
            p.setColor(a(col, 0.30f + 0.15f * k));
            c.drawRect(tl.x - tl.w * 0.5f * k, tl.y - 700f, tl.x + tl.w * 0.5f * k, tl.y, p);
            p.setColor(a(col, 0.85f));
            c.drawRect(tl.x - tl.w * 0.5f - 1.5f, tl.y - 700f, tl.x - tl.w * 0.5f + 1.5f, tl.y, p);
            c.drawRect(tl.x + tl.w * 0.5f - 1.5f, tl.y - 700f, tl.x + tl.w * 0.5f + 1.5f, tl.y, p);
            rf.set(tl.x - tl.w * 0.5f, tl.y - 8f, tl.x + tl.w * 0.5f, tl.y + 8f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f);
            p.setColor(a(col, 0.9f));
            c.drawOval(rf, p);
        }
    }

    private void drawBurst(Canvas c, Burst b) {
        float k = b.life / Math.max(0.01f, b.maxLife);
        if (b.shape == 0) {
            circ(c, b.x, b.y, b.w * (1.1f - 0.1f * k), a(b.color, 0.28f * k), true, 1f);
        } else if (b.shape == 1) {
            float ex = b.x + (float) Math.cos(b.ang) * b.len, ey = b.y + (float) Math.sin(b.ang) * b.len;
            line(c, b.x, b.y, ex, ey, a(b.color, 0.9f * k), b.w * (0.4f + 0.6f * k));
        } else {
            p.setStyle(Paint.Style.FILL);
            p.setColor(a(b.color, 0.75f * k));
            float w = b.w * (0.3f + 0.7f * k);
            c.drawRect(b.x - w * 0.5f, b.y - 700f, b.x + w * 0.5f, b.y, p);
        }
    }

    // ---------------- projectiles / beam ----------------

    private void drawProj(Canvas c, Proj b, float t) {
        if (b.friendly) {
            if (b.kind == 0) {
                float ca = (float) Math.cos(b.ang), sa = (float) Math.sin(b.ang);
                line(c, b.x - ca * 14f, b.y - sa * 14f, b.x + ca * 8f, b.y + sa * 8f, a(b.color, 0.35f), 12f);
                line(c, b.x - ca * 12f, b.y - sa * 12f, b.x + ca * 8f, b.y + sa * 8f, b.color, 6f);
                line(c, b.x - ca * 6f, b.y - sa * 6f, b.x + ca * 8f, b.y + sa * 8f, 0xFFFFFFFF, 2.5f);
            } else {
                float r = b.r * (1f + 0.08f * (float) Math.sin(t * 30f));
                circ(c, b.x, b.y, r * 1.9f, a(b.color, 0.18f), true, 1f);
                circ(c, b.x, b.y, r * 1.3f, a(b.color, 0.5f), true, 1f);
                circ(c, b.x, b.y, r * 0.8f, b.color, true, 1f);
                circ(c, b.x, b.y, r * 0.45f, 0xFFFFFFFF, true, 1f);
            }
        } else if (b.kind == 4) {
            path.reset();
            path.moveTo(b.x - 18f, 0f);
            path.lineTo(b.x, -42f);
            path.lineTo(b.x + 18f, 0f);
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(a(b.color, 0.35f));
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f);
            p.setColor(0xFFFF5060);
            c.drawPath(path, p);
        } else {
            circ(c, b.x, b.y, b.r * 1.8f, a(0xFFFF3050, 0.22f), true, 1f);
            circ(c, b.x, b.y, b.r, 0xFFFF5060, true, 1f);
            circ(c, b.x, b.y, b.r * 0.5f, 0xFFFFFFFF, true, 1f);
        }
    }

    private void drawBeam(Canvas c, Player pl, float t) {
        float ca = (float) Math.cos(pl.beamAng), sa = (float) Math.sin(pl.beamAng);
        float len = 1000f;
        float ex = pl.beamX + ca * len, ey = pl.beamY + sa * len;
        float fade = Math.min(1f, pl.beamT / 0.15f);
        float pulse = 1f + 0.12f * (float) Math.sin(t * 60f);
        int col = pl.color();
        line(c, pl.beamX, pl.beamY, ex, ey, a(col, 0.22f * fade), 56f * pulse * fade);
        line(c, pl.beamX, pl.beamY, ex, ey, a(col, 0.45f * fade), 38f * pulse * fade);
        line(c, pl.beamX, pl.beamY, ex, ey, a(col, 0.9f), 22f * pulse * fade);
        line(c, pl.beamX, pl.beamY, ex, ey, 0xFFFFFFFF, 10f * pulse * fade);
        circ(c, pl.beamX, pl.beamY, 30f * pulse * fade, a(col, 0.5f), true, 1f);
        circ(c, pl.beamX, pl.beamY, 16f * pulse * fade, 0xFFFFFFFF, true, 1f);
    }

    // ---------------- player ----------------

    private void drawPlayer(Canvas c, Game g, float t) {
        Player pl = g.p;
        // shadow
        float sh = Math.max(0.2f, 1f - Math.abs(Math.min(0f, pl.y)) / 260f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(a(0xFF000000, 0.35f * sh));
        rf.set(pl.x - 26f * sh, -5f, pl.x + 26f * sh, 5f);
        c.drawOval(rf, p);
        // aura
        boolean aura = pl.transformed || pl.st == Player.ST_CHARGE;
        if (aura) {
            int ac = pl.transformed ? Data.GOLD : pl.color();
            float lv = pl.st == Player.ST_CHARGE ? 0.6f + 0.6f * (pl.ki / pl.maxKi()) : 1.1f;
            float pu = 1f + 0.07f * (float) Math.sin(t * 18f);
            circ(c, pl.x, pl.y - 44f, 74f * lv * pu, a(ac, 0.07f), true, 1f);
            circ(c, pl.x, pl.y - 44f, 54f * lv * pu, a(ac, 0.12f), true, 1f);
            circ(c, pl.x, pl.y - 44f, 36f * lv * pu, a(ac, 0.18f), true, 1f);
        }
        float al = 1f;
        if (pl.hitBlink > 0f && ((int) (t * 30f) & 1) == 0) al = 0.35f;
        if (pl.st == Player.ST_DEAD) al = 0.8f;
        Rig.draw(c, p, pl.x, pl.y, pl.face, pl.pose, pl.color(), al, true, pl.hairLen(), pl.hairColor());
        // beam charge orb
        if (pl.st == Player.ST_SKILL && pl.cls == 1 && pl.skill == 1 && pl.skStage == 0) {
            float k = Math.min(1f, pl.stT / 0.5f);
            float hx = pl.x + pl.face * 40f, hy = pl.y - 62f;
            circ(c, hx, hy, 6f + 22f * k, a(pl.color(), 0.5f), true, 1f);
            circ(c, hx, hy, 3f + 10f * k, 0xFFFFFFFF, true, 1f);
        }
    }

    // ---------------- enemies ----------------

    private void drawEnemy(Canvas c, Game g, Enemy e, float t) {
        if (e.hp <= 0f) return;
        float al = e.alpha;
        boolean wind = Ai.winding(e);
        boolean flashWhite = e.flash > 0f || (wind && ((int) (t * 18f) & 1) == 0);
        int base = Game.enemyColor(e);
        int col = flashWhite ? 0xFFFFFFFF : base;
        float cx = e.x, by = e.y;
        int f = e.face;
        float ph = t * 12f + cx * 0.05f;
        float shake = (wind && e.type != Ent.GRUNT) ? (float) Math.sin(t * 60f) * 1.5f : 0f;
        if (al < 1f) {
            circ(c, cx, by - e.h * 0.5f, e.h * 0.7f * (1f - al), a(base, 0.5f * (1f - al)), false, 3f);
        }
        switch (e.type) {
            case Ent.GRUNT: {
                float lx = Math.abs(e.vx) > 20f ? (float) Math.sin(ph) * 8f : 0f;
                gl(c, cx - 4f, by - 16f, cx - 5f + lx, by, a(col, al), 4f);
                gl(c, cx + 4f, by - 16f, cx + 5f - lx, by, a(col, al), 4f);
                gl(c, cx, by - 18f, cx, by - 34f, a(col, al), 24f);
                line(c, cx, by - 18f, cx, by - 34f, a(0xFF14060C, al), 16f);
                path.reset();
                path.moveTo(cx - f * 10f, by - 38f);
                path.lineTo(cx - f * 20f, by - 44f);
                path.lineTo(cx - f * 10f, by - 30f);
                path.lineTo(cx - f * 18f, by - 26f);
                path.lineTo(cx - f * 10f, by - 22f);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3f);
                p.setColor(a(col, al));
                c.drawPath(path, p);
                line(c, cx + f * 3f, by - 34f, cx + f * 9f, by - 34f, a(0xFFFFFFFF, al), 3f);
                float ax, ay;
                if (e.state == Ai.S_WIND) { ax = cx - f * 6f; ay = by - 56f; }
                else if (e.state == Ai.S_STRIKE) { ax = cx + f * 50f; ay = by - 30f; }
                else { ax = cx + f * 16f; ay = by - 16f; }
                gl(c, cx + f * 8f, by - 28f, ax, ay, a(col, al), 5f);
                break;
            }
            case Ent.FLYER: {
                float cy = by - 15f + shake;
                float s = 1f + (e.state == Ai.S_AIM ? 0.15f * (float) Math.sin(t * 40f) : 0f);
                path.reset();
                path.moveTo(cx, cy - 20f * s);
                path.lineTo(cx + 15f * s, cy);
                path.lineTo(cx, cy + 20f * s);
                path.lineTo(cx - 15f * s, cy);
                path.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(a(0xFF1A0E2A, al));
                c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(8f);
                p.setColor(a(col, 0.22f * al));
                c.drawPath(path, p);
                p.setStrokeWidth(3f);
                p.setColor(a(col, al));
                c.drawPath(path, p);
                circ(c, cx, cy, 5f, a(0xFFFFFFFF, al), true, 1f);
                for (int i = 0; i < 2; i++) {
                    float an = t * 4f + i * 3.1416f;
                    circ(c, cx + (float) Math.cos(an) * 22f, cy + (float) Math.sin(an) * 9f, 2.5f, a(col, al), true, 1f);
                }
                break;
            }
            case Ent.BRUTE: {
                float cy = by - 42f;
                float lx = Math.abs(e.vx) > 20f ? (float) Math.sin(ph * 0.6f) * 8f : 0f;
                gl(c, cx - 11f, by - 22f, cx - 12f + lx, by, a(col, al), 10f);
                gl(c, cx + 11f, by - 22f, cx + 12f - lx, by, a(col, al), 10f);
                hex(cx + shake, cy, 33f, 0f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(a(0xFF2A1A04, al));
                c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(9f);
                p.setColor(a(col, 0.2f * al));
                c.drawPath(path, p);
                p.setStrokeWidth(4f);
                p.setColor(a(col, al));
                c.drawPath(path, p);
                hex(cx + shake, cy, 19f, 0.5236f);
                p.setStrokeWidth(2.5f);
                p.setColor(a(col, 0.7f * al));
                c.drawPath(path, p);
                line(c, cx + f * 4f, cy - 8f, cx + f * 14f, cy - 8f, a(0xFFFF3030, al), 4f);
                float ax, ay;
                if (e.state == Ai.S_WIND) { ax = cx - f * 8f; ay = cy - 52f; }
                else if (e.state == Ai.S_STRIKE) { ax = cx + f * 70f; ay = cy; }
                else { ax = cx + f * 24f; ay = cy + 22f; }
                gl(c, cx + f * 22f, cy - 6f, ax, ay, a(col, al), 11f);
                break;
            }
            case Ent.BOSS1: {
                float cy = by - 88f + shake;
                float lx = Math.abs(e.vx) > 20f ? (float) Math.sin(ph * 0.5f) * 10f : 0f;
                boolean ph2 = e.phase == 1;
                int bc = flashWhite ? 0xFFFFFFFF : (ph2 ? 0xFFFF6A3A : base);
                gl(c, cx - 22f, by - 40f, cx - 24f + lx, by, a(bc, al), 18f);
                gl(c, cx + 22f, by - 40f, cx + 24f - lx, by, a(bc, al), 18f);
                hex(cx, cy, 70f, 0f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF1C0808);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(16f);
                p.setColor(a(bc, 0.2f));
                c.drawPath(path, p);
                p.setStrokeWidth(6f);
                p.setColor(bc);
                c.drawPath(path, p);
                hex(cx, cy, 44f, t * 0.8f);
                p.setStrokeWidth(3f);
                p.setColor(a(bc, 0.8f));
                c.drawPath(path, p);
                float pulse = 0.5f + 0.5f * (float) Math.sin(t * 6f);
                circ(c, cx, cy, 14f + 5f * pulse, a(bc, 0.5f), true, 1f);
                circ(c, cx, cy, 7f, 0xFFFFFFFF, true, 1f);
                // horns
                gl(c, cx - 36f, cy - 58f, cx - 54f, cy - 98f, bc, 8f);
                gl(c, cx + 36f, cy - 58f, cx + 54f, cy - 98f, bc, 8f);
                // eyes
                line(c, cx - 24f, cy - 22f, cx - 8f, cy - 18f, 0xFFFFFFFF, 5f);
                line(c, cx + 24f, cy - 22f, cx + 8f, cy - 18f, 0xFFFFFFFF, 5f);
                // arms
                boolean raise = e.state == Ai.B_D || e.state == Ai.B_A;
                float ay = raise ? cy - 120f : cy + 30f;
                gl(c, cx - 66f, cy - 8f, cx - 84f, ay, bc, 16f);
                gl(c, cx + 66f, cy - 8f, cx + 84f, ay, bc, 16f);
                break;
            }
            default: {
                float cy = by - 48f + shake;
                float hover = (float) Math.sin(t * 3f) * 3f;
                cy += hover;
                path.reset();
                path.moveTo(cx, cy - 52f);
                path.lineTo(cx + 28f, cy);
                path.lineTo(cx, cy + 52f);
                path.lineTo(cx - 28f, cy);
                path.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(a(0xFF140A22, al));
                c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(12f);
                p.setColor(a(col, 0.2f * al));
                c.drawPath(path, p);
                p.setStrokeWidth(4f);
                p.setColor(a(col, al));
                c.drawPath(path, p);
                circ(c, cx, cy, 11f, a(0xFFFFFFFF, al), true, 1f);
                circ(c, cx + f * 3f, cy, 5f, a(0xFFFF3050, al), true, 1f);
                for (int i = 0; i < 4; i++) {
                    float an = t * 2.2f + i * 1.5708f;
                    float sx = cx + (float) Math.cos(an) * 46f, sy = cy + (float) Math.sin(an) * 20f;
                    path.reset();
                    path.moveTo(sx, sy - 8f);
                    path.lineTo(sx + 5f, sy);
                    path.lineTo(sx, sy + 8f);
                    path.lineTo(sx - 5f, sy);
                    path.close();
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(a(col, 0.85f * al));
                    c.drawPath(path, p);
                }
                break;
            }
        }
        // hp bar
        if (!e.boss) {
            float w = Math.max(26f, e.w * 2f + 8f);
            float y = by - e.h - 16f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(a(0xFF000000, 0.55f * al));
            c.drawRect(cx - w * 0.5f, y, cx + w * 0.5f, y + 4f, p);
            p.setColor(a(base, al));
            c.drawRect(cx - w * 0.5f, y, cx - w * 0.5f + w * Math.max(0f, e.hp / e.maxHp), y + 4f, p);
        }
        if (wind) {
            float y = by - e.h - 34f;
            if (e.type == Ent.BOSS1) y = by - 220f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFF04D);
            path.reset();
            path.moveTo(cx, y);
            path.lineTo(cx + 8f, y + 16f);
            path.lineTo(cx - 8f, y + 16f);
            path.close();
            c.drawPath(path, p);
            p.setColor(0xFF000000);
            c.drawRect(cx - 1.5f, y + 5f, cx + 1.5f, y + 10f, p);
            c.drawRect(cx - 1.5f, y + 12f, cx + 1.5f, y + 14.5f, p);
        }
    }
}
