package com.foonyresso.ouravex;

import com.foonyresso.ouravex.Ent.Enemy;

/** Active skills for both classes plus the transformation. Skill state lives in Player. */
final class Skills {
    private Skills() { }

    static boolean tryCast(Player p, int i) {
        if (!p.unlocked[i] || p.skCd[i] > 0f) return false;
        Game g = p.g;
        if (i == 3 && p.transformed) {
            endTransform(p);
            p.skCd[3] = 1.5f;
            return true;
        }
        float cost = p.skillCost(i);
        if (p.ki < cost) {
            if (p.bufSkill[i] > 0.2f) g.fx.text(p.x, p.y - 120f, "Мало ki", 0xFF6A7BFF, 13f);
            return false;
        }
        p.ki -= cost;
        p.skCd[i] = Data.SKILL_CD[p.cls][i];
        p.st = Player.ST_SKILL;
        p.stT = 0f;
        p.skill = i;
        p.skStage = 0;
        p.skI = 0;
        p.skNext = 0.12f;
        p.beamOn = false;
        g.audio.setCharging(false, 0f);
        if (i == 3) {
            g.audio.play(Audio.TRANSFORM, 1f);
            g.slowmo(0.4f, 0.9f);
            g.fx.screenFlash(Data.GOLD, 0.35f);
            return true;
        }
        if (p.cls == 0) {
            if (i == 0) {
                p.blitzN = 0;
                float cx = p.x, cy = p.y;
                for (int k = 0; k < 4; k++) {
                    Enemy best = null;
                    float bd = 520f * 520f;
                    for (int j = 0; j < g.enemies.size(); j++) {
                        Enemy e = g.enemies.get(j);
                        if (e.hp <= 0f || e.spawnT > 0f) continue;
                        boolean used = false;
                        for (int m = 0; m < p.blitzN; m++) if (p.blitzE[m] == e) used = true;
                        if (used) continue;
                        float dx = e.x - cx, dy = e.y - cy;
                        float d = dx * dx + dy * dy;
                        if (d < bd) { bd = d; best = e; }
                    }
                    if (best == null) break;
                    p.blitzE[p.blitzN++] = best;
                    cx = best.x; cy = best.y;
                }
                p.skNext = 0.04f;
                p.invuln = Math.max(p.invuln, 0.5f + Math.max(1, p.blitzN) * 0.13f);
                g.audio.play(Audio.WHOOSH, 0.8f);
            } else if (i == 1) {
                g.audio.play(Audio.WHOOSH, 0.7f);
            } else {
                p.skNext = 0.12f;
            }
        } else {
            if (i == 2) p.skNext = 0.12f;
        }
        return true;
    }

    static void end(Player p) {
        p.st = Player.ST_FREE;
        p.stT = 0f;
        p.beamOn = false;
        p.vx *= 0.3f;
    }

    static void endTransform(Player p) {
        if (!p.transformed) return;
        p.transformed = false;
        p.g.fx.ring(p.x, p.y - 40f, 10f, 90f, 0.3f, Data.GOLD);
        p.g.audio.play(Audio.WHOOSH, 0.6f);
    }

    private static float hx(Player p) { return p.x + p.face * 40f; }
    private static float hy(Player p) { return p.y - 62f; }

    static float distToSeg(float px, float py, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        float l2 = dx * dx + dy * dy;
        float t = l2 > 0f ? ((px - x0) * dx + (py - y0) * dy) / l2 : 0f;
        t = Player.clamp(t, 0f, 1f);
        float cx = x0 + dx * t - px, cy = y0 + dy * t - py;
        return (float) Math.sqrt(cx * cx + cy * cy);
    }

    /** returns whether gravity applies */
    static boolean update(Player p, float dt) {
        p.blendK = 40f;
        if (p.skill == 3) return transUpdate(p, dt);
        if (p.cls == 0) {
            switch (p.skill) {
                case 0: return blitz(p, dt);
                case 1: return slam(p, dt);
                default: return barrage(p, dt);
            }
        }
        switch (p.skill) {
            case 0: return orb(p, dt);
            case 1: return beam(p, dt);
            default: return rain(p, dt);
        }
    }

    // ---------------- Kai ----------------

    private static boolean blitz(Player p, float dt) {
        Game g = p.g;
        p.vx = 0f; p.vy = 0f;
        p.invuln = Math.max(p.invuln, 0.15f);
        int steps = Math.max(1, p.blitzN);
        if (p.skI < steps && p.stT >= p.skNext) {
            blitzStep(p);
            p.skI++;
            p.skNext += 0.13f;
        }
        if (p.skI >= steps && p.stT >= p.skNext + 0.06f) {
            end(p);
            return true;
        }
        return false;
    }

    private static void blitzStep(Player p) {
        Game g = p.g;
        float x0 = p.x, y0 = p.y;
        float mul = p.skMul(0);
        int col = p.color();
        if (p.blitzN == 0) {
            float tx = Player.clamp(p.x + p.face * 250f, 24f, Game.WORLD_W - 24f);
            float bx0 = Math.min(x0, tx) - 20f, bx1 = Math.max(x0, tx) + 20f;
            for (int k = 0; k < 6; k++) {
                g.fx.ghost(Rig.DASH, x0 + (tx - x0) * k / 6f, y0, p.face, col, p.hairLen(), p.hairColor(), 0.2f);
            }
            g.fx.blink(x0, y0, col);
            p.x = tx;
            g.fx.blink(p.x, p.y, col);
            for (int j = 0; j < g.enemies.size(); j++) {
                Enemy e = g.enemies.get(j);
                if (e.hp <= 0f || e.spawnT > 0f) continue;
                if (e.x > bx0 && e.x < bx1 && e.y > p.y - 120f && e.y - e.h < p.y + 10f) {
                    g.hitEnemy(e, 16f * mul, p.face * 300f, -200f, 0.5f, 2, p.face, col);
                }
            }
            g.audio.play(Audio.TELEPORT, 0.9f);
            return;
        }
        Enemy e = p.blitzE[p.skI];
        if (e == null || e.hp <= 0f) return;
        boolean last = p.skI == p.blitzN - 1;
        p.face = (p.skI % 2 == 0) ? 1 : -1;
        float tx = Player.clamp(e.x - p.face * (e.w + 30f), 24f, Game.WORLD_W - 24f);
        float ty = e.y < -6f ? e.y : 0f;
        for (int k = 0; k < 4; k++) {
            g.fx.ghost(Rig.DASH, x0 + (tx - x0) * k / 4f, y0 + (ty - y0) * k / 4f, tx >= x0 ? 1 : -1, col,
                    p.hairLen(), p.hairColor(), 0.2f);
        }
        g.fx.blink(x0, y0, col);
        p.x = tx; p.y = ty;
        g.fx.blink(p.x, p.y, col);
        g.audio.play(Audio.TELEPORT, 0.85f);
        g.hitEnemy(e, (last ? 22f : 13f) * mul, p.face * (last ? 380f : 90f), last ? -340f : -50f,
                last ? 0.7f : 0.35f, last ? 2 : 1, p.face, col);
        if (last) {
            g.fx.explosion(e.x, e.y - e.h * 0.5f, 60f, col);
        }
        p.ki = Math.min(p.maxKi(), p.ki + 3f);
    }

    private static boolean slam(Player p, float dt) {
        Game g = p.g;
        p.vx = 0f;
        float t = p.stT;
        if (p.skStage == 0) {
            if (g.fx.rnd() < 0.8f) {
                float a = g.fx.rnd(0f, 6.28f);
                float r = g.fx.rnd(50f, 90f);
                g.fx.add(Fx.DOT, p.x + (float) Math.cos(a) * r, p.y - 90f + (float) Math.sin(a) * r * 0.6f,
                        -(float) Math.cos(a) * r * 6f, -(float) Math.sin(a) * r * 3.6f, 0.16f, 4f, 0f, p.color(), 0f, 0f);
            }
            if (t >= 0.24f) {
                p.skStage = 1;
                slamImpact(p);
            }
        } else if (t >= 0.62f) {
            end(p);
        }
        return true;
    }

    private static void slamImpact(Player p) {
        Game g = p.g;
        float mul = p.skMul(1);
        int col = p.color();
        g.audio.play(Audio.SLAM, 1f);
        g.shake(0.55f);
        g.stop(0.08f);
        g.fx.ring(p.x, p.y, 10f, 270f, 0.4f, col);
        g.fx.ring(p.x, p.y, 6f, 200f, 0.28f, 0xFFFFFFFF);
        g.fx.flash(p.x, p.y - 10f, 60f, 10f, 0.15f, 0xFFFFFFFF);
        for (int k = -7; k <= 7; k++) {
            float px = p.x + k * 34f;
            g.fx.add(Fx.SPARK, px, p.y, 0f, -g.fx.rnd(420f, 950f), 0.3f, 4.5f, 0f, k % 2 == 0 ? 0xFFFFFFFF : col, 0f, 0f);
        }
        g.fx.dust(p.x, p.y, 1, 8);
        g.fx.dust(p.x, p.y, -1, 8);
        for (int j = 0; j < g.enemies.size(); j++) {
            Enemy e = g.enemies.get(j);
            if (e.hp <= 0f || e.spawnT > 0f) continue;
            if (Math.abs(e.x - p.x) < 260f && Math.abs(e.y - p.y) < 170f) {
                int s = e.x >= p.x ? 1 : -1;
                g.hitEnemy(e, 24f * mul, s * 280f, -430f, 0.9f, 2, s, col);
            }
        }
        g.burst(0, p.x, p.y, 0f, 0f, 270f, 0.25f, col);
    }

    private static boolean barrage(Player p, float dt) {
        Game g = p.g;
        p.vx = p.face * 40f;
        int col = p.color();
        while (p.skI < 12 && p.stT >= p.skNext) {
            barrageHit(p, p.skI == 11);
            p.skI++;
            p.skNext += 0.075f;
        }
        if (p.skI >= 12 && p.stT >= p.skNext + 0.05f) end(p);
        return true;
    }

    private static void barrageHit(Player p, boolean last) {
        Game g = p.g;
        float mul = p.skMul(2);
        int col = p.color();
        float reach = last ? 170f : 130f;
        float bx0 = p.face > 0 ? p.x - 6f : p.x - reach, bx1 = p.face > 0 ? p.x + reach : p.x + 6f;
        for (int j = 0; j < g.enemies.size(); j++) {
            Enemy e = g.enemies.get(j);
            if (e.hp <= 0f || e.spawnT > 0f) continue;
            if (e.x + e.w > bx0 && e.x - e.w < bx1 && e.y > p.y - 120f && e.y - e.h < p.y + 8f) {
                if (last) g.hitEnemy(e, 16f * mul, p.face * 430f, -260f, 0.7f, 2, p.face, col);
                else g.hitEnemy(e, 5.2f * mul, p.face * 30f, -8f, 0.2f, 3, p.face, col);
            }
        }
        float px = p.x + p.face * g.fx.rnd(40f, reach * 0.8f);
        float py = p.y - g.fx.rnd(30f, 90f);
        g.fx.hit(px, py, p.face, last ? 0.9f : 0.35f, col);
        g.audio.play(last ? Audio.HIT_H : Audio.HIT_L, 0.55f);
        if (last) {
            g.fx.explosion(p.x + p.face * 110f, p.y - 55f, 70f, col);
            g.shake(0.4f);
            g.audio.play(Audio.EXPLODE, 0.7f);
        }
    }

    // ---------------- Rey ----------------

    private static void converge(Player p, float strength) {
        Game g = p.g;
        float a = g.fx.rnd(0f, 6.28f);
        float r = g.fx.rnd(30f, 60f) * strength;
        float x = hx(p), y = hy(p);
        g.fx.add(Fx.DOT, x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r,
                -(float) Math.cos(a) * r * 7f, -(float) Math.sin(a) * r * 7f, 0.15f, 3.5f, 0f, p.color(), 0f, 0f);
    }

    private static boolean orb(Player p, float dt) {
        Game g = p.g;
        p.vx = Player.approach(p.vx, 0f, 2000f * dt);
        if (p.skStage == 0) {
            converge(p, 1f);
            converge(p, 1f);
            if (p.stT >= 0.22f) {
                p.skStage = 1;
                float x = hx(p), y = hy(p);
                float a = p.aimAngle(x, y);
                g.newProj(1, x, y, (float) Math.cos(a) * 680f, (float) Math.sin(a) * 680f, 24f,
                        34f * p.skMul(0), 0.9f, 120f, p.color(), true);
                g.audio.play(Audio.BLAST, 1f);
                g.fx.flash(x, y, 34f, 6f, 0.12f, 0xFFFFFFFF);
                g.fx.ring(x, y, 10f, 60f, 0.2f, p.color());
                g.shake(0.15f);
                p.vx = -p.face * 160f;
            }
        } else if (p.stT >= 0.42f) {
            end(p);
        }
        return true;
    }

    private static boolean beam(Player p, float dt) {
        Game g = p.g;
        float t = p.stT;
        int col = p.color();
        if (p.skStage == 0) {
            p.vx = 0f;
            converge(p, 1.6f);
            converge(p, 1.6f);
            converge(p, 1.6f);
            if (t < 0.5f && g.fx.rnd() < 0.15f) g.fx.ring(hx(p), hy(p), 60f, 6f, 0.2f, col);
            if (t >= 0.5f) {
                p.skStage = 1;
                p.beamOn = true;
                p.beamT = 0.85f;
                p.beamTick = 0f;
                p.beamX = hx(p); p.beamY = hy(p);
                p.beamAng = p.aimAngle(p.beamX, p.beamY);
                g.audio.play(Audio.BEAM, 1f);
                g.fx.flash(p.beamX, p.beamY, 50f, 10f, 0.15f, 0xFFFFFFFF);
                g.shake(0.35f);
            }
        } else if (p.skStage == 1) {
            p.beamX = hx(p); p.beamY = hy(p);
            p.vx = -p.face * 50f;
            p.beamT -= dt;
            p.beamTick -= dt;
            g.shake(0.02f);
            float ca = (float) Math.cos(p.beamAng), sa = (float) Math.sin(p.beamAng);
            float len = 1000f;
            float ex = p.beamX + ca * len, ey = p.beamY + sa * len;
            if (p.beamTick <= 0f) {
                p.beamTick = 0.08f;
                float mul = p.skMul(1);
                for (int j = 0; j < g.enemies.size(); j++) {
                    Enemy e = g.enemies.get(j);
                    if (e.hp <= 0f || e.spawnT > 0f) continue;
                    float d = distToSeg(e.x, e.y - e.h * 0.5f, p.beamX, p.beamY, ex, ey);
                    if (d < 22f + Math.max(e.w, e.h * 0.35f)) {
                        g.hitEnemy(e, 7.5f * mul, p.face * 60f, 0f, 0.12f, 3, p.face, col);
                        g.fx.sparks(e.x, e.y - e.h * 0.5f, 3, 380f, 0.2f, 3f, 0xFFFFFFFF, ca, sa, 3.5f);
                    }
                }
            }
            for (int k = 0; k < 2; k++) {
                float d = g.fx.rnd(0f, len);
                g.fx.add(Fx.SPARK, p.beamX + ca * d, p.beamY + sa * d + g.fx.rnd(-10f, 10f), ca * 900f, sa * 900f,
                        0.18f, 3f, 0f, k == 0 ? 0xFFFFFFFF : col, 0f, 0f);
            }
            g.fx.sparks(p.beamX, p.beamY, 2, 300f, 0.2f, 3f, 0xFFFFFFFF, 0f, 0f, 6.3f);
            if (p.beamT <= 0f) { p.beamOn = false; p.skStage = 2; }
        } else if (t >= 1.6f) {
            end(p);
        }
        return true;
    }

    private static boolean rain(Player p, float dt) {
        Game g = p.g;
        p.vx = Player.approach(p.vx, 0f, 2000f * dt);
        while (p.skI < 9 && p.stT >= p.skNext) {
            float x = hx(p), y = hy(p);
            float a = -1.5708f + (p.skI - 4) * 0.2f + p.face * 0.3f;
            g.newProj(2, x, y, (float) Math.cos(a) * 520f, (float) Math.sin(a) * 520f, 8f,
                    10f * p.skMul(2), 1.8f, 55f, p.color(), true);
            g.audio.play(Audio.SHOOT, 0.4f);
            g.fx.flash(x, y, 10f, 3f, 0.06f, 0xFFFFFFFF);
            p.skI++;
            p.skNext += 0.055f;
        }
        if (p.skI >= 9 && p.stT >= p.skNext + 0.2f) end(p);
        return true;
    }

    // ---------------- transformation ----------------

    private static boolean transUpdate(Player p, float dt) {
        Game g = p.g;
        p.vx = 0f;
        float t = p.stT;
        g.fx.aura(p.x, p.y, Data.GOLD, 2.4f);
        g.fx.aura(p.x, p.y, 0xFFFFFFFF, 1.6f);
        if (g.fx.rnd() < 0.2f) g.fx.ring(p.x, p.y - 40f, 90f, 8f, 0.25f, Data.GOLD);
        g.shake(0.015f);
        if (t >= 0.9f) {
            p.transformed = true;
            g.fx.explosion(p.x, p.y - 40f, 130f, Data.GOLD);
            g.fx.screenFlash(0xFFFFFFFF, 0.6f);
            g.fx.shake(0.4f);
            g.audio.play(Audio.EXPLODE, 0.6f);
            end(p);
        }
        return true;
    }

    // ---------------- pose ----------------

    static void pose(Player p, float[] tgt) {
        switch (p.skill) {
            case 0:
                if (p.cls == 0) {
                    if (p.blitzN == 0) Rig.copy(tgt, Rig.DASH);
                    else Rig.copy(tgt, (p.skI & 1) == 1 ? Rig.PUNCH1_H : Rig.KICK1_H);
                } else Rig.copy(tgt, Rig.CAST);
                break;
            case 1:
                if (p.cls == 0) Rig.copy(tgt, p.skStage == 0 ? Rig.SLAM_UP : Rig.SLAM_DN);
                else Rig.copy(tgt, p.skStage == 1 ? Rig.BEAM : Rig.CAST);
                break;
            case 2:
                if (p.cls == 0) {
                    int m = p.skI & 3;
                    Rig.copy(tgt, m == 0 ? Rig.PUNCH1_H : (m == 1 ? Rig.PUNCH2_H : (m == 2 ? Rig.KICK1_H : Rig.PUNCH2_H)));
                } else Rig.copy(tgt, Rig.CAST);
                break;
            default:
                Rig.copy(tgt, Rig.CHARGE);
                tgt[5] += p.g.fx.rnd(-2f, 2f);
                tgt[7] += p.g.fx.rnd(-2f, 2f);
                tgt[6] -= 3f;
                break;
        }
    }
}
