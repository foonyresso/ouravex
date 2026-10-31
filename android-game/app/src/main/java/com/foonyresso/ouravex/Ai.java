package com.foonyresso.ouravex;

import com.foonyresso.ouravex.Ent.Enemy;
import com.foonyresso.ouravex.Ent.Tele;

/** Enemy and boss behaviour. Telegraphs (warning shapes) are created through Game.addTele. */
final class Ai {
    private Ai() { }

    static final int S_CHASE = 0, S_WIND = 1, S_STRIKE = 2, S_RECOVER = 3, S_CHARGE = 4, S_AIM = 5;
    // boss states
    static final int B_INTRO = 0, B_IDLE = 1, B_A = 2, B_B = 3, B_C = 4, B_D = 5, B_RECOVER = 6;

    static boolean winding(Enemy e) {
        switch (e.type) {
            case Ent.GRUNT: return e.state == S_WIND;
            case Ent.FLYER: return e.state == S_AIM;
            case Ent.BRUTE: return e.state == S_WIND || e.state == S_AIM;
            case Ent.BOSS1: return e.state == B_A || e.state == B_B || e.state == B_D;
            default: return e.state == B_A || e.state == B_B || e.state == B_C;
        }
    }

    static void update(Game g, Enemy e, float dt) {
        e.t += dt;
        e.flash -= dt;
        e.cd -= dt;
        if (e.spawnT > 0f) {
            e.spawnT -= dt;
            e.alpha = 1f - Math.max(0f, e.spawnT) / 0.35f;
            return;
        }
        if (e.type != Ent.BOSS2) e.alpha = 1f;
        switch (e.type) {
            case Ent.GRUNT: grunt(g, e, dt); break;
            case Ent.FLYER: flyer(g, e, dt); break;
            case Ent.BRUTE: brute(g, e, dt); break;
            case Ent.BOSS1: boss1(g, e, dt); break;
            default: boss2(g, e, dt); break;
        }
    }

    // ---------------- helpers ----------------

    private static boolean overlapsPlayer(Player p, float x0, float x1, float y0, float y1) {
        float top = p.y - (p.st == Player.ST_CROUCH ? 46f : (p.st == Player.ST_SLIDE ? 34f : 90f));
        return x1 > p.x - 14f && x0 < p.x + 14f && y1 > top && y0 < p.y + 4f;
    }

    private static void ground(Game g, Enemy e, float dt) {
        e.vy += Game.GRAV * dt;
        e.x += e.vx * dt;
        float y0 = e.y;
        e.y += e.vy * dt;
        e.jt -= dt;
        float fl = g.floorBelow(e.x, y0);
        if (e.vy >= 0f && e.y >= fl) { e.y = fl; e.vy = 0f; }
        e.x = Player.clamp(e.x, 20f, Game.WORLD_W - 20f);
    }

    private static void air(Enemy e, float dt) {
        e.x += e.vx * dt;
        e.y += e.vy * dt;
        if (e.y > -30f) e.y = -30f;
        e.x = Player.clamp(e.x, 20f, Game.WORLD_W - 20f);
    }

    private static void jumpUp(Game g, Enemy e) {
        Player p = g.p;
        if (e.vy == 0f && e.jt <= 0f && p.y < e.y - 60f && Math.abs(p.x - e.x) < 320f) {
            e.vy = -640f; e.jt = 1.1f;
        }
    }

    private static int sgn(float v) { return v >= 0f ? 1 : -1; }

    // ---------------- grunt ----------------

    private static void grunt(Game g, Enemy e, float dt) {
        Player p = g.p;
        float dx = p.x - e.x, adx = Math.abs(dx);
        float as = g.atkSpd;
        if (e.stun > 0f) {
            e.stun -= dt;
            e.state = S_CHASE; e.t = 0f;
            e.vx = Player.approach(e.vx, 0f, 700f * dt);
            ground(g, e, dt);
            return;
        }
        switch (e.state) {
            case S_CHASE: {
                e.face = sgn(dx);
                float want = adx > 48f ? e.face * e.speed : 0f;
                e.vx = Player.approach(e.vx, want, 900f * dt);
                jumpUp(g, e);
                if (adx < 62f && Math.abs(p.y - e.y) < 70f && e.cd <= 0f && e.vy == 0f && p.st != Player.ST_DEAD) {
                    e.state = S_WIND; e.t = 0f; e.n1 = 0;
                    g.audio.play(Audio.WARN, 0.18f);
                }
                break;
            }
            case S_WIND:
                e.vx = Player.approach(e.vx, 0f, 1500f * dt);
                if (e.t >= (e.n1 == 1 ? 0.3f : 0.5f) / as) {
                    e.state = S_STRIKE; e.t = 0f; e.a1 = 0f;
                    e.vx = e.face * 240f;
                    g.audio.play(Audio.WHOOSH, 0.35f);
                    float sx = e.x + e.face * 46f, sy = e.y - 30f;
                    g.fx.sparks(sx, sy, 5, 380f, 0.15f, 2.5f, 0xFFFF4D6D, e.face, 0f, 0.8f);
                    g.fx.add(Fx.SPARK, sx - e.face * 24f, sy, e.face * 1100f, 0f, 0.09f, 4f, 0f, 0xFFFF4D6D, 0f, 0f);
                }
                break;
            case S_STRIKE:
                e.vx = Player.approach(e.vx, 0f, 900f * dt);
                if (e.a1 == 0f && overlapsPlayer(p, e.face > 0 ? e.x : e.x - 74f, e.face > 0 ? e.x + 74f : e.x,
                        e.y - 54f, e.y)) {
                    e.a1 = 1f;
                    g.hurtPlayer(e.dmg, e.x, 230f);
                }
                if (e.t >= 0.13f) {
                    if (g.diff == 2 && e.n1 == 0) {
                        e.n1 = 1; e.state = S_WIND; e.t = 0f; e.face = sgn(dx);
                    } else {
                        e.state = S_RECOVER; e.t = 0f; e.cd = 1.1f / as;
                    }
                }
                break;
            default:
                e.vx = Player.approach(e.vx, 0f, 900f * dt);
                if (e.t >= 0.45f) { e.state = S_CHASE; e.t = 0f; }
                break;
        }
        ground(g, e, dt);
    }

    // ---------------- flyer ----------------

    private static void flyer(Game g, Enemy e, float dt) {
        Player p = g.p;
        float dx = p.x - e.x;
        float as = g.atkSpd;
        e.face = sgn(dx);
        if (e.a3 == 0f) e.a3 = 0.1f + g.rnd.nextFloat() * 6f;
        if (e.stun > 0f) {
            e.stun -= dt;
            e.state = S_CHASE; e.t = 0f;
            e.vx = Player.approach(e.vx, 0f, 800f * dt);
            e.vy = Player.approach(e.vy, 0f, 800f * dt);
            air(e, dt);
            return;
        }
        float cx = e.x, cy = e.y - e.h * 0.5f;
        if (e.state == S_CHASE) {
            float tx = p.x + e.a1 * 270f;
            float ty = -(150f + 22f * (float) Math.sin(g.time * 2f + e.a3));
            e.vx = Player.approach(e.vx, Player.clamp((tx - e.x) * 2.2f, -e.speed, e.speed), 700f * dt);
            e.vy = Player.approach(e.vy, Player.clamp((ty - e.y) * 2.2f, -e.speed, e.speed), 700f * dt);
            if (e.cd <= 0f && Math.abs(dx) < 640f && p.st != Player.ST_DEAD) {
                float a = (float) Math.atan2((p.y - 45f) - cy, p.x - cx);
                Tele tl = g.addTele(1, 1, cx, cy, a, 380f, 6f, 0.75f / as, e.dmg, e);
                tl.count = g.diff == 2 ? 3 : 1;
                tl.spread = 0.24f;
                tl.color = 0xFFB06BFF;
                e.state = S_AIM; e.t = 0f;
            }
        } else {
            e.vx = Player.approach(e.vx, 0f, 900f * dt);
            e.vy = Player.approach(e.vy, 0f, 900f * dt);
            if (e.t >= 0.75f / as + 0.35f) {
                e.state = S_CHASE; e.t = 0f; e.cd = 2.0f / as;
                if (g.rnd.nextBoolean()) e.a1 = -e.a1;
            }
        }
        air(e, dt);
    }

    // ---------------- brute ----------------

    private static void brute(Game g, Enemy e, float dt) {
        Player p = g.p;
        float dx = p.x - e.x, adx = Math.abs(dx);
        float as = g.atkSpd;
        if (e.stun > 0f) {
            e.stun -= dt;
            e.state = S_CHASE; e.t = 0f;
            e.vx = Player.approach(e.vx, 0f, 700f * dt);
            ground(g, e, dt);
            return;
        }
        switch (e.state) {
            case S_CHASE: {
                e.face = sgn(dx);
                float want = adx > 66f ? e.face * e.speed : 0f;
                e.vx = Player.approach(e.vx, want, 700f * dt);
                jumpUp(g, e);
                if (e.cd <= 0f && e.vy == 0f && p.st != Player.ST_DEAD) {
                    if (adx < 84f && Math.abs(p.y - e.y) < 70f) {
                        e.state = S_WIND; e.t = 0f;
                        g.audio.play(Audio.WARN, 0.25f);
                    } else if (adx > 180f && adx < 520f) {
                        e.state = S_AIM; e.t = 0f; e.a1 = e.face;
                        Tele tl = g.addTele(1, 2, e.x, -38f, e.face > 0 ? 0f : 3.1416f, 460f, 70f, 0.85f / as, 0f, e);
                        tl.color = 0xFFFFB020;
                    }
                }
                break;
            }
            case S_WIND:
                e.vx = Player.approach(e.vx, 0f, 1500f * dt);
                if (e.t >= 0.6f / as) { e.state = S_STRIKE; e.t = 0f; e.a1 = 0f; g.audio.play(Audio.WHOOSH, 0.5f); }
                break;
            case S_STRIKE:
                if (e.a1 == 0f && overlapsPlayer(p, e.face > 0 ? e.x : e.x - 100f, e.face > 0 ? e.x + 100f : e.x,
                        e.y - 80f, e.y)) {
                    e.a1 = 1f;
                    g.hurtPlayer(e.dmg, e.x, 340f);
                }
                if (e.t < dt * 1.5f) {
                    g.fx.sparks(e.x + e.face * 60f, e.y - 40f, 8, 450f, 0.18f, 3f, 0xFFFFB020, e.face, 0f, 1.2f);
                    g.fx.shake(0.1f);
                }
                if (e.t >= 0.16f) { e.state = S_RECOVER; e.t = 0f; e.cd = 1.4f / as; e.a3 = 0f; }
                break;
            case S_AIM:
                e.vx = Player.approach(e.vx, 0f, 1500f * dt);
                if (e.t >= 0.85f / as) {
                    e.state = S_CHARGE; e.t = 0f; e.a2 = 0f;
                    e.vx = e.a1 * 560f;
                    g.audio.play(Audio.WHOOSH, 0.7f);
                }
                break;
            case S_CHARGE:
                if (e.a2 == 0f && overlapsPlayer(p, e.x - e.w, e.x + e.w, e.y - e.h, e.y)) {
                    e.a2 = 1f;
                    g.hurtPlayer(e.dmg * 1.3f, e.x, 380f);
                }
                if (e.t % 0.06f < dt) g.fx.dust(e.x, 0f, e.face, 2);
                if (e.t >= 0.8f || e.x <= 40f || e.x >= Game.WORLD_W - 40f) {
                    e.state = S_RECOVER; e.t = 0f; e.cd = 2.0f / as; e.a3 = 1.1f;
                    g.fx.shake(0.15f);
                }
                break;
            default:
                e.vx = Player.approach(e.vx, 0f, 900f * dt);
                if (e.t >= (e.a3 > 0f ? e.a3 : 0.5f)) { e.state = S_CHASE; e.t = 0f; e.a3 = 0f; }
                break;
        }
        ground(g, e, dt);
    }

    // ---------------- boss 1: Colossus ----------------

    private static void bossPhase(Game g, Enemy e) {
        if (e.phase == 0 && e.hp < e.maxHp * 0.5f) {
            e.phase = 1;
            g.flashBanner("ЯРІСТЬ!", 0xFFFF3050);
            g.audio.play(Audio.BOSS, 0.9f);
            g.shake(0.6f);
            g.fx.explosion(e.x, e.y - e.h * 0.5f, 120f, Game.enemyColor(e));
            g.fx.screenFlash(0xFFFF3050, 0.4f);
            if (e.type == Ent.BOSS2) {
                for (int i = 0; i < 2; i++) {
                    Enemy m = g.spawnEnemy(Ent.FLYER, e.x + (i == 0 ? -80f : 80f), e.y - 60f);
                    m.spawnT = 0.35f;
                    g.fx.blink(m.x, m.y, 0xFFB06BFF);
                }
            }
        }
    }

    private static void boss1(Game g, Enemy e, float dt) {
        Player p = g.p;
        float dx = p.x - e.x;
        float as = g.atkSpd * (e.phase == 1 ? 1.2f : 1f);
        int col = Game.enemyColor(e);
        switch (e.state) {
            case B_INTRO:
                e.vy += Game.GRAV * 1.3f * dt;
                e.y += e.vy * dt;
                if (e.y >= 0f) {
                    e.y = 0f; e.vy = 0f;
                    g.fx.explosion(e.x, -10f, 150f, col);
                    g.shake(0.8f);
                    g.audio.play(Audio.SLAM, 1f);
                    if (Math.abs(p.x - e.x) < 170f && p.y > -30f) g.hurtPlayer(e.dmg, e.x, 320f);
                    e.state = B_IDLE; e.t = 0f; e.cd = 1.6f;
                }
                return;
            case B_IDLE: {
                bossPhase(g, e);
                e.face = sgn(dx);
                float want = Math.abs(dx) > 200f ? e.face * e.speed * (e.phase == 1 ? 1.3f : 1f) : 0f;
                e.vx = Player.approach(e.vx, want, 600f * dt);
                if (e.cd <= 0f && g.bossIntro <= 0f && p.st != Player.ST_DEAD) pickBoss1(g, e, as);
                break;
            }
            case B_A: { // stomp
                float T = e.a3;
                float k = Math.min(1f, e.t / T);
                e.x = e.a1 + (e.a2 - e.a1) * k;
                e.y = -(float) Math.sin(Math.PI * k) * 260f;
                e.face = sgn(p.x - e.x);
                if (e.t >= T) {
                    e.y = 0f; e.x = e.a2;
                    g.fx.explosion(e.x, -10f, 130f, col);
                    g.shake(0.6f);
                    g.audio.play(Audio.SLAM, 1f);
                    float dmg = e.dmg * 0.8f;
                    g.newProj(4, e.x - 40f, 0f, -380f, 0f, 26f, dmg, 1.4f, 0f, col, false);
                    g.newProj(4, e.x + 40f, 0f, 380f, 0f, 26f, dmg, 1.4f, 0f, col, false);
                    if (e.phase == 1 || g.diff == 2) {
                        g.newProj(4, e.x - 40f, 0f, -250f, 0f, 26f, dmg, 2.0f, 0f, col, false);
                        g.newProj(4, e.x + 40f, 0f, 250f, 0f, 26f, dmg, 2.0f, 0f, col, false);
                    }
                    if (e.phase == 1) {
                        for (int k2 = 0; k2 < 8; k2++) {
                            float a = k2 * 0.7854f + 0.2f;
                            g.newProj(3, e.x, -90f, (float) Math.cos(a) * 250f, (float) Math.sin(a) * 250f, 9f, dmg, 3.2f, 0f, col, false);
                        }
                    }
                    e.state = B_RECOVER; e.t = 0f; e.a3 = 0.9f;
                    e.cd = 1.8f / as;
                }
                return;
            }
            case B_B: // charge wind-up
                e.vx = Player.approach(e.vx, 0f, 1500f * dt);
                e.x += e.vx * dt;
                if (e.t >= e.a3) {
                    e.state = B_C; e.t = 0f; e.n1 = 0;
                    e.vx = e.a1 * 760f;
                    g.audio.play(Audio.WHOOSH, 0.9f);
                }
                break;
            case B_C: { // charge
                if (e.n1 == 0 && overlapsPlayer(p, e.x - e.w, e.x + e.w, e.y - e.h, e.y)) {
                    e.n1 = 1;
                    g.hurtPlayer(e.dmg * 1.2f, e.x, 420f);
                }
                if (e.t % 0.05f < dt) g.fx.dust(e.x, 0f, e.face, 2);
                g.shake(0.015f);
                if (e.t >= 1.1f || e.x <= 90f || e.x >= Game.WORLD_W - 90f) {
                    if (g.diff == 2 && e.a2 == 0f) {
                        e.a2 = 1f;
                        e.a1 = sgn(p.x - e.x);
                        e.face = (int) e.a1;
                        e.vx = 0f;
                        e.state = B_B; e.t = 0f; e.a3 = 0.5f / as;
                        Tele tl = g.addTele(1, 2, e.x, -60f, e.a1 > 0 ? 0f : 3.1416f, 900f, 120f, e.a3, 0f, e);
                        tl.color = col;
                    } else {
                        e.state = B_RECOVER; e.t = 0f; e.a3 = 1.2f; e.cd = 1.5f / as;
                        g.fx.shake(0.3f);
                    }
                }
                break;
            }
            case B_D: // pillars
                e.vx = Player.approach(e.vx, 0f, 1500f * dt);
                if (e.t >= e.a3) { e.state = B_RECOVER; e.t = 0f; e.a3 = 0.5f; e.cd = 1.6f / as; }
                break;
            default: // recover
                e.vx = Player.approach(e.vx, 0f, 900f * dt);
                if (e.t >= e.a3) { e.state = B_IDLE; e.t = 0f; }
                break;
        }
        ground(g, e, dt);
    }

    private static void pickBoss1(Game g, Enemy e, float as) {
        Player p = g.p;
        int col = Game.enemyColor(e);
        int pat;
        do { pat = g.rnd.nextInt(3); } while (pat == e.pattern);
        e.pattern = pat;
        e.t = 0f;
        g.audio.play(Audio.WARN, 0.5f);
        if (pat == 0) {
            e.state = B_A;
            e.a1 = e.x;
            e.a2 = Player.clamp(p.x, 100f, Game.WORLD_W - 100f);
            e.a3 = 1.0f / as;
            Tele tl = g.addTele(0, 0, e.a2, 0f, 0f, 0f, 150f, e.a3, e.dmg, e);
            tl.color = col;
        } else if (pat == 1) {
            e.state = B_B;
            e.a1 = sgn(p.x - e.x);
            e.face = (int) e.a1;
            e.a2 = 0f;
            e.a3 = 0.9f / as;
            Tele tl = g.addTele(1, 2, e.x, -60f, e.a1 > 0 ? 0f : 3.1416f, 900f, 120f, e.a3, 0f, e);
            tl.color = col;
        } else {
            e.state = B_D;
            int n = 4 + e.phase * 2 + (g.diff == 2 ? 1 : 0);
            float maxT = 0f;
            for (int i = 0; i < n; i++) {
                float tx = Player.clamp(p.x + (i - (n - 1) * 0.5f) * 115f + (i == 0 ? 0f : g.rnd.nextFloat() * 60f - 30f),
                        60f, Game.WORLD_W - 60f);
                float T = 0.95f / as + i * 0.17f;
                maxT = Math.max(maxT, T);
                Tele tl = g.addTele(2, 0, tx, 0f, 0f, 0f, 85f, T, e.dmg, e);
                tl.color = col;
            }
            e.a3 = maxT + 0.4f;
        }
    }

    // ---------------- boss 2: Spectre ----------------

    private static void boss2(Game g, Enemy e, float dt) {
        Player p = g.p;
        float dx = p.x - e.x;
        float as = g.atkSpd * (e.phase == 1 ? 1.2f : 1f);
        int col = Game.enemyColor(e);
        float cy = e.y - e.h * 0.5f;
        e.face = sgn(dx);
        switch (e.state) {
            case B_INTRO: {
                float k = 1f - Math.max(0f, g.bossIntro) / 2.4f;
                e.alpha = Math.min(1f, k * 1.3f);
                float ease = 1f - (1f - k) * (1f - k);
                e.y = -520f + (-90f + 520f) * ease;
                if (g.bossIntro <= 0f) {
                    e.state = B_IDLE; e.t = 0f; e.cd = 1.4f; e.alpha = 1f;
                    g.fx.explosion(e.x, e.y - e.h * 0.5f, 90f, col);
                }
                return;
            }
            case B_IDLE: {
                bossPhase(g, e);
                float tx = p.x + (e.a1 == 0f ? 1f : e.a1) * 330f;
                tx = Player.clamp(tx, 100f, Game.WORLD_W - 100f);
                float ty = -90f + (float) Math.sin(g.time * 1.6f) * 14f;
                e.vx = Player.approach(e.vx, Player.clamp((tx - e.x) * 2f, -e.speed, e.speed), 500f * dt);
                e.vy = Player.approach(e.vy, Player.clamp((ty - e.y) * 3f, -e.speed, e.speed), 500f * dt);
                if (e.cd <= 0f && g.bossIntro <= 0f && p.st != Player.ST_DEAD) pickBoss2(g, e, as);
                break;
            }
            case B_A: // beams
                e.vx = Player.approach(e.vx, 0f, 700f * dt);
                e.vy = Player.approach(e.vy, 0f, 700f * dt);
                if (e.t >= e.a3) { e.state = B_RECOVER; e.t = 0f; e.a3 = 0.6f; e.cd = 1.6f / as; }
                break;
            case B_B: // orb rings
                e.vx = Player.approach(e.vx, 0f, 700f * dt);
                e.vy = Player.approach(e.vy, 0f, 700f * dt);
                if (e.t >= e.a3) { e.state = B_RECOVER; e.t = 0f; e.a3 = 0.6f; e.cd = 1.5f / as; }
                break;
            case B_C: // slash
                e.vx = 0f; e.vy = 0f;
                if (e.t >= e.a2) {
                    float ex = Player.clamp(e.a1, 60f, Game.WORLD_W - 60f);
                    float ey = Math.min(-60f, e.a3);
                    g.fx.blink(e.x, e.y, col);
                    g.fx.ring(e.x, cy, 10f, 80f, 0.25f, col);
                    e.x = ex; e.y = ey;
                    g.fx.blink(e.x, e.y, col);
                    g.audio.play(Audio.TELEPORT, 0.9f);
                    g.shake(0.25f);
                    e.n1++;
                    if (e.phase == 1 && e.n1 < 2) startSlash(g, e, as);
                    else { e.state = B_RECOVER; e.t = 0f; e.a3 = 0.7f; e.cd = 1.5f / as; }
                }
                break;
            default:
                e.vx = Player.approach(e.vx, 0f, 700f * dt);
                e.vy = Player.approach(e.vy, 0f, 700f * dt);
                if (e.t >= e.a3) { e.state = B_IDLE; e.t = 0f; if (g.rnd.nextBoolean()) e.a1 = -(e.a1 == 0f ? 1f : e.a1); }
                break;
        }
        if (e.state != B_C) {
            e.x += e.vx * dt;
            e.y += e.vy * dt;
        }
        e.x = Player.clamp(e.x, 40f, Game.WORLD_W - 40f);
        e.y = Math.min(e.y, -40f);
    }

    private static void startSlash(Game g, Enemy e, float as) {
        Player p = g.p;
        int col = Game.enemyColor(e);
        int side = g.rnd.nextBoolean() ? 1 : -1;
        float bx = Player.clamp(p.x + side * 380f, 80f, Game.WORLD_W - 80f);
        float by = -80f;
        g.fx.blink(e.x, e.y, col);
        e.x = bx; e.y = by;
        g.fx.blink(e.x, e.y, col);
        float cy = by - e.h * 0.5f;
        float a = (float) Math.atan2((p.y - 45f) - cy, p.x - bx);
        float T = 0.85f / as;
        Tele tl = g.addTele(1, 0, bx, cy, a, 900f, 60f, T, e.dmg * 1.3f, e);
        tl.color = col;
        e.state = B_C; e.t = 0f;
        e.a1 = bx + (float) Math.cos(a) * 850f;
        e.a3 = cy + (float) Math.sin(a) * 850f + e.h * 0.5f;
        e.a2 = T;
    }

    private static void pickBoss2(Game g, Enemy e, float as) {
        Player p = g.p;
        int col = Game.enemyColor(e);
        int pat;
        do { pat = g.rnd.nextInt(3); } while (pat == e.pattern);
        e.pattern = pat;
        e.t = 0f;
        float cy = e.y - e.h * 0.5f;
        g.audio.play(Audio.WARN, 0.5f);
        if (pat == 0) {
            e.state = B_A;
            int n = 1 + e.phase * 2 + (g.diff == 2 ? 1 : 0);
            float a = (float) Math.atan2((p.y - 45f) - cy, p.x - e.x);
            float T = 1.05f / as;
            for (int i = 0; i < n; i++) {
                float ang = a + (i - (n - 1) * 0.5f) * 0.38f;
                Tele tl = g.addTele(1, 0, e.x, cy, ang, 1200f, 46f, T, e.dmg, e);
                tl.color = col;
            }
            e.a3 = T + 0.7f;
        } else if (pat == 1) {
            e.state = B_B;
            int waves = 2 + e.phase;
            int count = 12 + e.phase * 2;
            float off = g.rnd.nextFloat();
            for (int w = 0; w < waves; w++) {
                Tele tl = g.addTele(0, 1, e.x, cy, off + w * 0.26f, 220f + w * 30f, 30f, 0.5f / as + w * 0.55f, e.dmg * 0.7f, e);
                tl.count = count;
                tl.spread = 6.2832f / count;
                tl.color = col;
            }
            e.a3 = 0.5f + waves * 0.55f + 0.3f;
        } else {
            e.n1 = 0;
            startSlash(g, e, as);
        }
    }
}
